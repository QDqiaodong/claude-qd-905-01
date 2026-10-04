package com.glass.works.service;

import com.glass.works.common.BizException;
import com.glass.works.common.ConflictException;
import com.glass.works.common.Dict;
import com.glass.works.domain.EdgeGrinding;
import com.glass.works.domain.TemperingBatch;
import com.glass.works.dto.EdgeStatDTO;
import com.glass.works.dto.FurnaceStatDTO;
import com.glass.works.dto.Requests.GrindingRequest;
import com.glass.works.dto.TemperingRequest;
import com.glass.works.dto.Views.FurnaceVO;
import com.glass.works.dto.Views.StageVO;
import com.glass.works.repository.EdgeGrindingRepository;
import com.glass.works.repository.TemperingBatchRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 磨边与钢化：边型进给校验、钢化温度区间、破碎片数校验与炉内推进。 */
@Service
public class ProcessService {

    private final EdgeGrindingRepository grindingRepository;
    private final TemperingBatchRepository temperingRepository;

    public ProcessService(EdgeGrindingRepository grindingRepository,
                          TemperingBatchRepository temperingRepository) {
        this.grindingRepository = grindingRepository;
        this.temperingRepository = temperingRepository;
    }

    // ---------------- 磨边 ----------------

    public List<EdgeGrinding> grindingList() {
        return grindingRepository.findAll();
    }

    public List<EdgeStatDTO> grindingStat() {
        return grindingRepository.statByEdgeType();
    }

    @Transactional
    public EdgeGrinding createGrinding(GrindingRequest request) {
        if (!StringUtils.hasText(request.getRecordNo())) {
            throw new BizException("磨边记录单号不能为空");
        }
        if (!Dict.isValidEdgeType(request.getEdgeType())) {
            throw new BizException("边型非法：" + request.getEdgeType());
        }
        if (request.getPieceCount() == null || request.getPieceCount() <= 0) {
            throw new BizException("加工片数必须为正整数");
        }
        if (request.getChipCount() == null) {
            request.setChipCount(0);
        }
        if (request.getChipCount() > request.getPieceCount()) {
            throw new BizException("崩边片数 " + request.getChipCount()
                    + " 不得超过加工片数 " + request.getPieceCount());
        }
        double[] range = Dict.edgeFeedRange(request.getEdgeType());
        if (request.getFeedSpeed() == null) {
            throw new BizException("请填写进给速度");
        }
        double speed = request.getFeedSpeed().doubleValue();
        if (speed < range[0] || speed > range[1]) {
            throw new BizException(Dict.edgeTypeName(request.getEdgeType()) + " 进给速度 " + speed
                    + " m/min 超出允许区间（" + range[0] + "~" + range[1] + " m/min）");
        }

        EdgeGrinding record = new EdgeGrinding();
        record.setRecordNo(request.getRecordNo());
        record.setCuttingOrderId(request.getCuttingOrderId());
        record.setBatchNo(request.getBatchNo());
        record.setMachine(request.getMachine());
        record.setEdgeType(request.getEdgeType());
        record.setWheelModel(request.getWheelModel());
        record.setFeedSpeed(request.getFeedSpeed().setScale(2, RoundingMode.HALF_UP));
        record.setPieceCount(request.getPieceCount());
        record.setChipCount(request.getChipCount());
        record.setChipNote(request.getChipNote());
        record.setOperator(request.getOperator());
        record.setGrindTime(LocalDateTime.now());
        return grindingRepository.save(record);
    }

    // ---------------- 钢化 ----------------

    public static final String STAGE_HEATING = "HEATING";
    public static final String STAGE_QUENCH = "QUENCH";
    public static final String STAGE_COOLING = "COOLING";
    public static final String STAGE_DONE = "DONE";

    /** 炉段顺序：加热 → 淬冷 → 冷却 → 完成（出炉为终态，不得回退 / 循环） */
    private static final List<String> STAGE_ORDER =
            List.of(STAGE_HEATING, STAGE_QUENCH, STAGE_COOLING, STAGE_DONE);

    /** 是否已有有效出炉结果：出炉片数为正即视为结果已确认，终态只读、不可改写 */
    private static boolean hasValidResult(TemperingBatch batch) {
        return batch.getOutputCount() != null && batch.getOutputCount() > 0;
    }

    public List<FurnaceVO> temperingList() {
        return temperingRepository.findAll().stream().map(this::toVO).toList();
    }

    public List<FurnaceStatDTO> furnaceStat() {
        return temperingRepository.statByFurnace();
    }

    public FurnaceVO furnace(Long id) {
        return toVO(temperingRepository.findById(id)
                .orElseThrow(() -> new BizException("钢化批次不存在：" + id)));
    }

    @Transactional
    public TemperingBatch createTempering(TemperingRequest request) {
        validateTempering(request);
        if (temperingRepository.existsByBatchNo(request.getBatchNo())) {
            throw new BizException("钢化批次号已存在：" + request.getBatchNo());
        }
        TemperingBatch batch = new TemperingBatch();
        batch.setBatchNo(request.getBatchNo());
        batch.setFurnaceNo(request.getFurnaceNo());
        batch.setGlassType(request.getGlassType());
        batch.setThicknessMm(request.getThicknessMm());
        batch.setTempLow(request.getTempLow());
        batch.setTempHigh(request.getTempHigh());
        batch.setHeatTemp(request.getHeatTemp());
        batch.setHeatSeconds(request.getHeatSeconds());
        batch.setQuenchPressure(request.getQuenchPressure());
        batch.setWarpage(request.getWarpage());
        batch.setParticleCount(request.getParticleCount());
        // 新建批次只是入炉，出炉片数必须在走完炉段后由出炉登记产生，这里不接受预置结果
        batch.setBrokenCount(0);
        batch.setOperator(request.getOperator());
        batch.setStage(STAGE_HEATING);
        batch.setCreatedAt(LocalDateTime.now());
        return temperingRepository.save(batch);
    }

    /** 只改工艺参数（滑块）并同步校验；已出炉批次终态冻结，参数也不允许再动 */
    @Transactional
    public FurnaceVO updateTempering(Long id, TemperingRequest request) {
        TemperingBatch batch = temperingRepository.findLockedById(id)
                .orElseThrow(() -> new BizException("钢化批次不存在：" + id));
        if (STAGE_DONE.equals(batch.getStage())) {
            throw new ConflictException(hasValidResult(batch)
                    ? "批次 " + batch.getBatchNo() + " 已出炉（合格 "
                            + batch.getGoodCount() + " 片、破碎率 " + batch.getBrokenRate()
                            + "%），工艺参数已随出炉结果冻结，不允许再调整"
                    : "批次 " + batch.getBatchNo()
                            + " 已走完炉段，仅可补录一次出炉片数，工艺参数不允许再调整");
        }
        if (request.getTempLow() != null) {
            batch.setTempLow(request.getTempLow());
        }
        if (request.getTempHigh() != null) {
            batch.setTempHigh(request.getTempHigh());
        }
        if (request.getHeatTemp() != null) {
            batch.setHeatTemp(request.getHeatTemp());
        }
        if (request.getHeatSeconds() != null) {
            batch.setHeatSeconds(request.getHeatSeconds());
        }
        if (request.getQuenchPressure() != null) {
            batch.setQuenchPressure(request.getQuenchPressure());
        }
        TemperingRequest check = new TemperingRequest();
        check.setTempLow(batch.getTempLow());
        check.setTempHigh(batch.getTempHigh());
        check.setHeatTemp(batch.getHeatTemp());
        check.setHeatSeconds(batch.getHeatSeconds());
        check.setQuenchPressure(batch.getQuenchPressure());
        check.setOutputCount(batch.getOutputCount());
        check.setBrokenCount(batch.getBrokenCount());
        validateTempering(check);
        temperingRepository.save(batch);
        return toVO(batch);
    }

    /** 炉内推进：加热段 → 淬冷段 → 冷却段 → 完成。完成即终态，再推进必须被拒绝（不回退到加热） */
    @Transactional
    public FurnaceVO advance(Long id) {
        TemperingBatch batch = temperingRepository.findLockedById(id)
                .orElseThrow(() -> new BizException("钢化批次不存在：" + id));
        String stage = batch.getStage() == null ? STAGE_HEATING : batch.getStage();
        if (STAGE_DONE.equals(stage)) {
            throw new ConflictException(hasValidResult(batch)
                    ? "批次 " + batch.getBatchNo() + " 已完成出炉（当前阶段："
                            + Dict.stageName(STAGE_DONE) + "，合格 " + batch.getGoodCount()
                            + " 片、破碎率 " + batch.getBrokenRate()
                            + "%），炉段单向不可回退，不能重新推进；刷新页面也不会回到加热段"
                    : "批次 " + batch.getBatchNo() + " 已走完炉段（当前阶段："
                            + Dict.stageName(STAGE_DONE)
                            + "），不能重新推进；该批次尚缺出炉片数，仅可补录一次出炉结果");
        }
        int index = STAGE_ORDER.indexOf(stage);
        if (index < 0) {
            throw new BizException("未知炉段：" + stage + "，无法推进");
        }
        batch.setStage(STAGE_ORDER.get(index + 1));
        temperingRepository.save(batch);
        return toVO(batch);
    }

    /**
     * 出炉登记 / 历史补录：
     * <ul>
     *   <li>仅冷却段可做正常出炉（加热 / 淬冷段玻璃还在炉内，不允许提前出炉）；</li>
     *   <li>阶段已完成（DONE）但片数缺失的历史记录，允许补录一次；</li>
     *   <li>已有有效出炉结果的批次，再次提交（即使片数不同）一律 409 拒绝，
     *       保留原出炉片数 / 合格片数 / 破碎率，不覆盖、不倒改。</li>
     * </ul>
     */
    @Transactional
    public FurnaceVO discharge(Long id, Integer outputCount, Integer brokenCount) {
        TemperingBatch batch = temperingRepository.findLockedById(id)
                .orElseThrow(() -> new BizException("钢化批次不存在：" + id));
        if (outputCount == null || outputCount <= 0) {
            throw new BizException("出炉片数必须为正整数");
        }
        int broken = brokenCount == null ? 0 : brokenCount;
        if (broken > outputCount) {
            throw new BizException("破碎片数 " + broken + " 不得超过出炉片数 " + outputCount);
        }

        String stage = batch.getStage() == null ? STAGE_HEATING : batch.getStage();
        if (STAGE_DONE.equals(stage)) {
            if (hasValidResult(batch)) {
                // 结果只确认一次：重复提交（片数相同或不同）都保留原结果并明确提示
                throw new ConflictException("批次 " + batch.getBatchNo()
                        + " 的出炉结果已确认（出炉 " + batch.getOutputCount() + " 片、破碎 "
                        + batch.getBrokenCount() + " 片、合格 " + batch.getGoodCount()
                        + " 片、破碎率 " + batch.getBrokenRate() + "%），不能重复出炉；"
                        + "本次提交的 " + outputCount + " 片未生效，原结果保持不变");
            }
            // 阶段已完成但片数缺失：仅此一种情况允许补录一次，写入后立即锁定
            batch.setOutputCount(outputCount);
            batch.setBrokenCount(broken);
            temperingRepository.save(batch);
            return toVO(batch);
        }
        if (!STAGE_COOLING.equals(stage)) {
            throw new ConflictException("批次 " + batch.getBatchNo() + " 当前处于「"
                    + Dict.stageName(stage) + "」，玻璃尚未走到冷却段，不能提前出炉；"
                    + "请先推进到冷却段后再登记出炉结果");
        }
        // 冷却段正常出炉（首次确认）
        batch.setOutputCount(outputCount);
        batch.setBrokenCount(broken);
        batch.setStage(STAGE_DONE);
        temperingRepository.save(batch);
        return toVO(batch);
    }

    private void validateTempering(TemperingRequest request) {
        if (request.getHeatTemp() != null) {
            double temp = request.getHeatTemp().doubleValue();
            if (temp < Dict.TEMP_MIN || temp > Dict.TEMP_MAX) {
                throw new BizException("钢化炉加热温度 " + request.getHeatTemp()
                        + "℃ 不在允许区间（" + (int) Dict.TEMP_MIN + "~" + (int) Dict.TEMP_MAX + "℃）内");
            }
        }
        if (request.getHeatSeconds() != null && (request.getHeatSeconds() < Dict.HEAT_SECONDS_MIN
                || request.getHeatSeconds() > Dict.HEAT_SECONDS_MAX)) {
            throw new BizException("加热时长 " + request.getHeatSeconds() + "s 不在允许区间（"
                    + Dict.HEAT_SECONDS_MIN + "~" + Dict.HEAT_SECONDS_MAX + "s）内");
        }
        if (request.getQuenchPressure() != null) {
            double pressure = request.getQuenchPressure().doubleValue();
            if (pressure < Dict.QUENCH_MIN || pressure > Dict.QUENCH_MAX) {
                throw new BizException("淬冷风压 " + pressure + "kPa 不在允许区间（"
                        + Dict.QUENCH_MIN + "~" + Dict.QUENCH_MAX + "kPa）内");
            }
        }
        if (request.getTempLow() != null && request.getTempHigh() != null
                && request.getTempLow() > request.getTempHigh()) {
            throw new BizException("温控区间下限 " + request.getTempLow()
                    + "℃ 不得高于上限 " + request.getTempHigh() + "℃");
        }
        if (request.getOutputCount() != null && request.getBrokenCount() != null
                && request.getBrokenCount() > request.getOutputCount()) {
            throw new BizException("破碎片数 " + request.getBrokenCount()
                    + " 不得超过出炉片数 " + request.getOutputCount());
        }
    }

    private FurnaceVO toVO(TemperingBatch batch) {
        FurnaceVO vo = new FurnaceVO();
        vo.setId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setFurnaceNo(batch.getFurnaceNo());
        vo.setGlassType(batch.getGlassType());
        vo.setGlassTypeName(Dict.glassTypeName(batch.getGlassType()));
        vo.setThicknessMm(batch.getThicknessMm());
        vo.setTempRange(batch.getTempRange());
        vo.setTempLow(batch.getTempLow());
        vo.setTempHigh(batch.getTempHigh());
        vo.setHeatTemp(batch.getHeatTemp());
        vo.setHeatSeconds(batch.getHeatSeconds());
        vo.setQuenchPressure(batch.getQuenchPressure());
        vo.setWarpage(batch.getWarpage());
        vo.setParticleCount(batch.getParticleCount());
        vo.setOutputCount(batch.getOutputCount());
        vo.setBrokenCount(batch.getBrokenCount());
        vo.setGoodCount(batch.getGoodCount());
        vo.setBrokenRate(batch.getBrokenRate());
        vo.setStage(batch.getStage());
        vo.setStageName(Dict.stageName(batch.getStage()));
        vo.setOperator(batch.getOperator());

        String stage = batch.getStage() == null ? STAGE_HEATING : batch.getStage();
        boolean finished = STAGE_DONE.equals(stage);
        boolean hasResult = hasValidResult(batch);
        vo.setFinished(finished);
        vo.setHasResult(hasResult);
        vo.setResultMissing(finished && !hasResult);
        vo.setCanAdvance(!finished);
        vo.setCanDischarge(STAGE_COOLING.equals(stage) || (finished && !hasResult));
        if (finished && hasResult) {
            // 已出炉且结果已确认：终态只读，明确告知不可继续操作的原因
            vo.setNextAction("已完成，无下一步操作");
            vo.setLockReason("已出炉且出炉结果已确认（出炉 " + batch.getOutputCount()
                    + " 片、破碎 " + batch.getBrokenCount() + " 片、合格 " + batch.getGoodCount()
                    + " 片、破碎率 " + batch.getBrokenRate() + "%），炉段不可回退、结果不可重复登记");
        } else if (finished) {
            // 阶段已完成但片数缺失：优先补录一次，而不是当成可重新生产的批次
            vo.setNextAction("补录出炉结果（仅此一次）");
            vo.setLockReason("炉段已走完但缺少出炉片数，不可重新推进 / 调整参数；请补录一次出炉片数与破碎片数，补录后转为只读");
        } else if (STAGE_COOLING.equals(stage)) {
            vo.setNextAction("登记出炉结果（首次确认）");
        } else {
            int index = STAGE_ORDER.indexOf(stage);
            String next = index >= 0 && index + 1 < STAGE_ORDER.size()
                    ? STAGE_ORDER.get(index + 1) : stage;
            vo.setNextAction("推进到「" + Dict.stageName(next) + "」");
        }

        int seconds = batch.getHeatSeconds() == null ? 0 : batch.getHeatSeconds();
        double heatTemp = batch.getHeatTemp() == null ? 0 : batch.getHeatTemp().doubleValue();
        List<StageVO> stages = new ArrayList<>();
        stages.add(new StageVO("HEATING", "加热段", heatTemp, seconds,
                "加热至 " + (int) heatTemp + "℃，保温 " + seconds + "s"));
        stages.add(new StageVO("QUENCH", "淬冷段", Math.round(heatTemp * 0.55 * 10.0) / 10.0, (int) (seconds * 0.25),
                "淬冷风压 " + (batch.getQuenchPressure() == null ? "-" : batch.getQuenchPressure()) + "kPa"));
        stages.add(new StageVO("COOLING", "冷却段", 60, (int) (seconds * 0.5),
                "波形度 " + (batch.getWarpage() == null ? "-" : batch.getWarpage()) + "mm/m"));
        vo.setStages(stages);
        return vo;
    }
}
