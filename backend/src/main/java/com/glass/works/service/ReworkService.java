package com.glass.works.service;

import com.glass.works.common.BizException;
import com.glass.works.common.ConflictException;
import com.glass.works.common.Dict;
import com.glass.works.domain.FinishedRack;
import com.glass.works.domain.InspectionRecord;
import com.glass.works.domain.ReworkBatch;
import com.glass.works.dto.Requests.RecheckRequest;
import com.glass.works.dto.Requests.ReworkActionRequest;
import com.glass.works.dto.Views.ReworkDetailVO;
import com.glass.works.repository.InspectionRecordRepository;
import com.glass.works.repository.ReworkBatchRepository;
import com.glass.works.repository.ReworkEventRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 返修闭环：隔离 → 处理中 → 待复检 → 已放行 的受控流转，
 * 检验、深加工与成品架共享的同一份批次质量状态。
 *
 * <ul>
 *   <li>任一工序判返修 → 形成 / 并入可追溯的隔离批次（保留来源工序、片数、缺陷、进度）。</li>
 *   <li>历史记录归并：同工序下批次号 / 订单号任一标识相同，或经同一张检验单的
 *       批次↔订单桥接确认同一实物才归并；无标识记录各自成批，不整体失联。</li>
 *   <li>所有流转走行级悲观锁 + 前置状态校验：并发相反结论只生效一个，
 *       未生效方收到 409 冲突说明，冲突尝试本身也留痕可审计。</li>
 *   <li>成品架上架 / 发运读取同一份状态：未闭环（隔离 / 处理中 / 待复检）一律拦截，
 *       只有已放行批次可继续；拦截事件写入批次时间线。</li>
 * </ul>
 */
@Service
public class ReworkService {

    private final ReworkBatchRepository batchRepository;
    private final ReworkEventRepository eventRepository;
    private final InspectionRecordRepository inspectionRepository;
    private final ReworkEventLogger eventLogger;

    public ReworkService(ReworkBatchRepository batchRepository,
                         ReworkEventRepository eventRepository,
                         InspectionRecordRepository inspectionRepository,
                         ReworkEventLogger eventLogger) {
        this.batchRepository = batchRepository;
        this.eventRepository = eventRepository;
        this.inspectionRepository = inspectionRepository;
        this.eventLogger = eventLogger;
    }

    // ---------------- 查询 ----------------

    public List<ReworkBatch> list(String status) {
        if (StringUtils.hasText(status)) {
            if (!Dict.isValidReworkStatus(status)) {
                throw new BizException("返修状态非法：" + status);
            }
            return batchRepository.findByStatusOrderByUpdatedAtDescIdDesc(status);
        }
        return batchRepository.findAllByOrderByUpdatedAtDescIdDesc();
    }

    /** 工作区待办：各状态批次数量 */
    public Map<String, Long> summary() {
        Map<String, Long> data = new LinkedHashMap<>();
        long total = 0;
        for (String status : Dict.REWORK_STATUSES) {
            long count = batchRepository.countByStatus(status);
            data.put(status, count);
            total += count;
        }
        data.put("TOTAL", total);
        return data;
    }

    /** 批次时间线：批次 + 事件回放 + 归并的检验记录 */
    public ReworkDetailVO detail(Long id) {
        ReworkBatch batch = batchRepository.findById(id)
                .orElseThrow(() -> new BizException("返修批次不存在：" + id));
        ReworkDetailVO vo = new ReworkDetailVO();
        vo.setBatch(batch);
        vo.setEvents(eventRepository.findByReworkIdOrderByIdAsc(id));
        vo.setRecords(inspectionRepository.findByReworkIdOrderByIdAsc(id));
        return vo;
    }

    // ---------------- 受控流转 ----------------

    /** 登记处置：隔离 → 处理中 */
    @Transactional
    public ReworkBatch dispose(Long id, ReworkActionRequest request) {
        String operator = requireOperator(request == null ? null : request.getOperator());
        String note = buildNote(request == null ? null : request.getMethod(),
                request == null ? null : request.getNote());
        return transition(id, Dict.RW_ISOLATED, Dict.RW_PROCESSING, Dict.EV_DISPOSE,
                null, operator, note, "登记处置");
    }

    /** 处置完成，提交复检：处理中 → 待复检 */
    @Transactional
    public ReworkBatch submitRecheck(Long id, ReworkActionRequest request) {
        String operator = requireOperator(request == null ? null : request.getOperator());
        String note = buildNote(request == null ? null : request.getMethod(),
                request == null ? null : request.getNote());
        return transition(id, Dict.RW_PROCESSING, Dict.RW_PENDING_RECHECK, Dict.EV_SUBMIT,
                null, operator, note, "提交复检");
    }

    /**
     * 复检判定：待复检 → 已放行（合格） / 隔离（再判返修）。
     * 两名检验员同时提交相反结论时，行锁保证只生效一个；
     * 后到者看到 409 冲突，且冲突尝试写入时间线。
     */
    @Transactional
    public ReworkBatch recheck(Long id, RecheckRequest request) {
        if (request == null || !Dict.VERDICT_PASS.equals(request.getVerdict())
                && !Dict.VERDICT_REWORK.equals(request.getVerdict())) {
            throw new BizException("复检结论只能是 合格(PASS) 或 返修(REWORK)");
        }
        String inspector = requireOperator(request.getInspector());
        String to = Dict.VERDICT_PASS.equals(request.getVerdict())
                ? Dict.RW_RELEASED : Dict.RW_ISOLATED;
        String note = Dict.VERDICT_PASS.equals(request.getVerdict())
                ? "复检合格，批次放行" : "复检仍不合格，批次回到隔离重新处置";
        if (StringUtils.hasText(request.getNote())) {
            note = note + "；" + request.getNote().trim();
        }
        return transition(id, Dict.RW_PENDING_RECHECK, to, Dict.EV_RECHECK,
                request.getVerdict(), inspector, note, "复检判定");
    }

    /**
     * 流转骨架：悲观锁读取 → 前置状态校验 → 落状态 → 写时间线。
     * 前置状态不符说明已有他人结论生效，记录冲突事件（独立事务）后抛 409。
     */
    private ReworkBatch transition(Long id, String expectFrom, String to, String eventType,
                                   String verdict, String operator, String note, String actionName) {
        ReworkBatch batch = batchRepository.findLockedById(id)
                .orElseThrow(() -> new BizException("返修批次不存在：" + id));
        if (!expectFrom.equals(batch.getStatus())) {
            String message = conflictMessage(batch, actionName);
            eventLogger.logIndependent(batch.getId(), Dict.EV_CONFLICT, batch.getStatus(), null,
                    verdict, operator, "尝试「" + actionName + "」未生效：" + message);
            throw new ConflictException(message);
        }
        String from = batch.getStatus();
        batch.setStatus(to);
        batch.setOperator(operator);
        if (StringUtils.hasText(note)) {
            batch.setNote(note);
        }
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);
        eventLogger.log(batch.getId(), eventType, from, to, verdict, operator, note);
        return batch;
    }

    /** 冲突说明：尽量指出已生效的结论是谁、是什么，让未生效一方看得明白 */
    private String conflictMessage(ReworkBatch batch, String actionName) {
        StringBuilder sb = new StringBuilder("操作冲突：「").append(actionName).append("」未生效。批次 ")
                .append(batch.getReworkNo()).append(" 当前状态为「")
                .append(Dict.reworkStatusName(batch.getStatus())).append("」");
        eventRepository.findTopByReworkIdAndEventTypeOrderByIdDesc(batch.getId(), Dict.EV_RECHECK)
                .ifPresent(last -> sb.append("；最近一次复检已由 ")
                        .append(last.getOperator() == null ? "他人" : last.getOperator())
                        .append(" 判定「").append(last.getVerdictName()).append("」"));
        sb.append("，请刷新后按最新状态处理");
        return sb.toString();
    }

    // ---------------- 检验判返修挂钩 ----------------

    /**
     * 检验判返修（由 InspectionService 在同一事务内调用）：
     * 同工序 + 标识命中在制批次则并入（片数累加、缺陷汇总，处理中 / 待复检的批次回到隔离）；
     * 否则新建隔离批次。已放行批次不复活，另起新闭环。
     */
    @Transactional
    public void onReworkVerdict(InspectionRecord record) {
        ReworkBatch hit = null;
        for (ReworkBatch open : batchRepository.findByStatusNot(Dict.RW_RELEASED)) {
            if (open.getProcessType().equals(record.getProcessType())
                    && sameLot(open, record.getBatchNo(), record.getOrderNo())) {
                hit = batchRepository.findLockedById(open.getId()).orElse(open);
                break;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        if (hit == null) {
            ReworkBatch batch = new ReworkBatch();
            batch.setReworkNo(nextReworkNo());
            batch.setProcessType(record.getProcessType());
            batch.setBatchNo(record.getBatchNo());
            batch.setOrderNo(record.getOrderNo());
            batch.setDefectSummary(Dict.defectTypeName(record.getDefectType()));
            batch.setSheetCount(record.getSheetCount() == null ? 1 : record.getSheetCount());
            batch.setStatus(Dict.RW_ISOLATED);
            batch.setOperator(record.getInspector());
            batch.setNote("检验单 " + record.getInspectNo() + " 判返修，批次隔离待处置");
            batch.setCreatedAt(now);
            batch.setUpdatedAt(now);
            batch = batchRepository.save(batch);
            record.setReworkId(batch.getId());
            eventLogger.log(batch.getId(), Dict.EV_CREATE, null, Dict.RW_ISOLATED,
                    Dict.VERDICT_REWORK, record.getInspector(),
                    "检验单 " + record.getInspectNo() + "（" + Dict.workStageName(record.getProcessType())
                            + "）判返修：" + Dict.defectTypeName(record.getDefectType())
                            + " " + record.getDefectSizeMm() + "mm，涉及 "
                            + (record.getSheetCount() == null ? 1 : record.getSheetCount()) + " 片");
            return;
        }
        String from = hit.getStatus();
        hit.setSheetCount((hit.getSheetCount() == null ? 0 : hit.getSheetCount())
                + (record.getSheetCount() == null ? 1 : record.getSheetCount()));
        hit.setDefectSummary(mergeDefect(hit.getDefectSummary(), record.getDefectType()));
        // 处理中 / 待复检期间又判返修：批次必须回到隔离，绝不允许带着新缺陷等放行
        if (!Dict.RW_ISOLATED.equals(from)) {
            hit.setStatus(Dict.RW_ISOLATED);
        }
        hit.setOperator(record.getInspector());
        hit.setUpdatedAt(now);
        batchRepository.save(hit);
        record.setReworkId(hit.getId());
        String note = "检验单 " + record.getInspectNo() + " 判返修并入本批次，涉及片数累计 "
                + hit.getSheetCount() + " 片";
        if (!from.equals(hit.getStatus())) {
            note += "；批次由「" + Dict.reworkStatusName(from) + "」回到「隔离」";
        }
        eventLogger.log(hit.getId(), Dict.EV_APPEND, from, hit.getStatus(),
                Dict.VERDICT_REWORK, record.getInspector(), note);
    }

    // ---------------- 成品架共享质量状态 ----------------

    /**
     * 上架 / 发运闸门：架上批次号或订单号命中未闭环返修批次即拦截，
     * 拦截事实写入批次时间线（独立事务，业务回滚也留痕）。
     */
    public void assertShippable(FinishedRack rack, String action) {
        List<ReworkBatch> blockers = blockingLoops(rack);
        if (blockers.isEmpty()) {
            return;
        }
        String reason = blockReason(blockers);
        for (ReworkBatch batch : blockers) {
            eventLogger.logIndependent(batch.getId(), Dict.EV_SHIP_BLOCK, batch.getStatus(), null,
                    null, null, "成品架 " + rack.getRackNo() + "（订单 " + rack.getOrderNo()
                            + "）" + action + "被拦截：批次未闭环");
        }
        throw new BizException("成品架 " + rack.getRackNo() + " " + action + "被拦截：" + reason);
    }

    /** 成品架列表回填共享质量状态与拦截原因（不落库） */
    public void applyQuality(List<FinishedRack> racks) {
        List<ReworkBatch> all = batchRepository.findAll();
        for (FinishedRack rack : racks) {
            List<ReworkBatch> loops = new ArrayList<>();
            for (ReworkBatch batch : all) {
                if (sameLot(batch, rack.getBatchNo(), rack.getOrderNo())) {
                    loops.add(batch);
                }
            }
            List<ReworkBatch> open = loops.stream()
                    .filter(b -> !Dict.RW_RELEASED.equals(b.getStatus())).toList();
            if (!open.isEmpty()) {
                // 展示最严格（最靠前）的未闭环状态：隔离 > 处理中 > 待复检
                ReworkBatch strictest = open.stream()
                        .min((a, b) -> Integer.compare(
                                Dict.REWORK_STATUSES.indexOf(a.getStatus()),
                                Dict.REWORK_STATUSES.indexOf(b.getStatus())))
                        .orElseThrow();
                rack.setQualityStatus(strictest.getStatus());
                rack.setBlockReason(blockReason(open));
            } else if (!loops.isEmpty()) {
                rack.setQualityStatus(Dict.RW_RELEASED);
                rack.setBlockReason(null);
            }
        }
    }

    private List<ReworkBatch> blockingLoops(FinishedRack rack) {
        if (!StringUtils.hasText(rack.getBatchNo()) && !StringUtils.hasText(rack.getOrderNo())) {
            return List.of();
        }
        List<ReworkBatch> blockers = new ArrayList<>();
        for (ReworkBatch batch : batchRepository.findByStatusNot(Dict.RW_RELEASED)) {
            if (sameLot(batch, rack.getBatchNo(), rack.getOrderNo())) {
                blockers.add(batch);
            }
        }
        return blockers;
    }

    private String blockReason(List<ReworkBatch> blockers) {
        List<String> parts = new ArrayList<>();
        for (ReworkBatch batch : blockers) {
            parts.add(batch.getReworkNo() + "（" + Dict.workStageName(batch.getProcessType())
                    + " · " + Dict.reworkStatusName(batch.getStatus()) + "）");
        }
        return "批次存在未闭环返修 " + String.join("、", parts) + "，须复检合格放行后方可继续";
    }

    // ---------------- 历史记录归并（启动回填，幂等） ----------------

    /**
     * 历史判返修记录归并建批：仅在返修批次表为空时执行。
     *
     * <p>归并取舍（可解释）：同一张检验单上同时出现的批次号与订单号视为同一实物的两个标识
     * （并查集桥接）；同工序下任一标识落进同一连通分量的记录归并为一批；
     * 只有订单号的记录不会被并进只有批次号的批次（无桥接即不同实物）；
     * 两个标识都没有的记录各自单独成批，保证旧记录整体不失联。</p>
     */
    @Transactional
    public void backfillLegacyIfEmpty() {
        if (batchRepository.count() > 0) {
            return;
        }
        List<InspectionRecord> reworks =
                inspectionRepository.findByVerdictOrderByIdAsc(Dict.VERDICT_REWORK);
        if (reworks.isEmpty()) {
            return;
        }
        // 并查集：key = 工序|B:批次号 / 工序|O:订单号
        Map<String, String> parent = new HashMap<>();
        for (InspectionRecord record : reworks) {
            String bKey = lotKey(record.getProcessType(), "B", record.getBatchNo());
            String oKey = lotKey(record.getProcessType(), "O", record.getOrderNo());
            if (bKey != null) {
                parent.putIfAbsent(bKey, bKey);
            }
            if (oKey != null) {
                parent.putIfAbsent(oKey, oKey);
            }
            if (bKey != null && oKey != null) {
                union(parent, bKey, oKey);
            }
        }
        // 分组：有标识的进连通分量，无标识的各自成组
        Map<String, List<InspectionRecord>> groups = new LinkedHashMap<>();
        for (InspectionRecord record : reworks) {
            String bKey = lotKey(record.getProcessType(), "B", record.getBatchNo());
            String oKey = lotKey(record.getProcessType(), "O", record.getOrderNo());
            String root;
            if (bKey != null) {
                root = find(parent, bKey);
            } else if (oKey != null) {
                root = find(parent, oKey);
            } else {
                root = record.getProcessType() + "|N:" + record.getInspectNo();
            }
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(record);
        }
        for (List<InspectionRecord> members : groups.values()) {
            createBatchFromGroup(members);
        }
    }

    private void createBatchFromGroup(List<InspectionRecord> members) {
        InspectionRecord first = members.get(0);
        ReworkBatch batch = new ReworkBatch();
        batch.setReworkNo(nextReworkNo());
        batch.setProcessType(first.getProcessType());
        batch.setBatchNo(members.stream().map(InspectionRecord::getBatchNo)
                .filter(StringUtils::hasText).findFirst().orElse(null));
        batch.setOrderNo(members.stream().map(InspectionRecord::getOrderNo)
                .filter(StringUtils::hasText).findFirst().orElse(null));
        List<String> defects = members.stream()
                .map(r -> Dict.defectTypeName(r.getDefectType())).distinct().toList();
        batch.setDefectSummary(String.join("、", defects));
        int sheets = members.stream()
                .mapToInt(r -> r.getSheetCount() == null ? 1 : r.getSheetCount()).sum();
        batch.setSheetCount(sheets);
        batch.setStatus(Dict.RW_ISOLATED);
        batch.setOperator(first.getInspector());
        LocalDateTime earliest = members.stream()
                .map(InspectionRecord::getInspectedAt).filter(java.util.Objects::nonNull)
                .min(LocalDateTime::compareTo).orElse(LocalDateTime.now());
        batch.setCreatedAt(earliest);
        batch.setUpdatedAt(earliest);
        batch.setNote(mergeNote(members));
        batch = batchRepository.save(batch);
        for (InspectionRecord member : members) {
            member.setReworkId(batch.getId());
        }
        eventLogger.log(batch.getId(), Dict.EV_MERGE, null, Dict.RW_ISOLATED,
                Dict.VERDICT_REWORK, first.getInspector(), batch.getNote());
    }

    /** 归并说明：逐条列出成员记录携带的标识，说明为何判为同一实物 */
    private String mergeNote(List<InspectionRecord> members) {
        List<String> parts = new ArrayList<>();
        for (InspectionRecord member : members) {
            String carry = StringUtils.hasText(member.getBatchNo())
                    && StringUtils.hasText(member.getOrderNo()) ? "批次+订单"
                    : StringUtils.hasText(member.getBatchNo()) ? "仅批次号"
                    : StringUtils.hasText(member.getOrderNo()) ? "仅订单号" : "无标识";
            parts.add(member.getInspectNo() + "(" + carry + ")");
        }
        String note = "历史归并建批：" + String.join("、", parts)
                + "；按「同工序 + 批次/订单标识桥接」确认为同一实物，共 " + members.size() + " 条记录";
        return note.length() > 255 ? note.substring(0, 252) + "..." : note;
    }

    // ---------------- 标识匹配与工具 ----------------

    /**
     * 同一实物判定：批次号或订单号任一标识相等（含交叉命中：
     * 记录上的批次号撞上批次的订单号，等同并查集里的桥接边）。
     */
    static boolean sameLot(ReworkBatch batch, String batchNo, String orderNo) {
        return StringUtils.hasText(batchNo)
                && (batchNo.equals(batch.getBatchNo()) || batchNo.equals(batch.getOrderNo()))
                || StringUtils.hasText(orderNo)
                && (orderNo.equals(batch.getOrderNo()) || orderNo.equals(batch.getBatchNo()));
    }

    private static String lotKey(String processType, String kind, String value) {
        return StringUtils.hasText(value) ? processType + "|" + kind + ":" + value : null;
    }

    private static String find(Map<String, String> parent, String key) {
        String root = key;
        while (!root.equals(parent.get(root))) {
            root = parent.get(root);
        }
        // 路径压缩
        String cur = key;
        while (!cur.equals(root)) {
            String next = parent.get(cur);
            parent.put(cur, root);
            cur = next;
        }
        return root;
    }

    private static void union(Map<String, String> parent, String a, String b) {
        String ra = find(parent, a);
        String rb = find(parent, b);
        if (!ra.equals(rb)) {
            parent.put(rb, ra);
        }
    }

    private String nextReworkNo() {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int seq = 1;
        String no;
        do {
            no = "RW-" + day + "-" + String.format("%03d", seq++);
        } while (batchRepository.existsByReworkNo(no));
        return no;
    }

    private static String mergeDefect(String summary, String defectType) {
        String name = Dict.defectTypeName(defectType);
        if (!StringUtils.hasText(summary)) {
            return name;
        }
        return summary.contains(name) ? summary : summary + "、" + name;
    }

    private static String requireOperator(String operator) {
        if (!StringUtils.hasText(operator)) {
            throw new BizException("经办人不能为空，请署名后再提交");
        }
        return operator.trim();
    }

    private static String buildNote(String method, String note) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(method)) {
            sb.append("处置方式：").append(method.trim());
        }
        if (StringUtils.hasText(note)) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append(note.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
