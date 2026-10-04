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

    public List<TemperingBatch> temperingList() {
        return temperingRepository.findAll();
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
        // 出炉片数 / 破碎片数只能由「出炉登记」首次确认，建档时不带出炉结果
        batch.setOutputCount(null);
        batch.setBrokenCount(0);
        batch.setOperator(request.getOperator());
        batch.setStage("HEATING");
        batch.setCreatedAt(LocalDateTime.now());
        return temperingRepository.save(batch);
    }

    /** 只改工艺参数（滑块）并同步校验；已完成批次历史数据只读 */
    @Transactional
    public TemperingBatch updateTempering(Long id, TemperingRequest request) {
        TemperingBatch batch = temperingRepository.findLockedById(id)
                .orElseThrow(() -> new BizException("钢化批次不存在：" + id));
        if (batch.isFinished()) {
            throw new ConflictException(batch.getLockReason());
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
        return temperingRepository.save(batch);
    }

    /** 炉内推进：加热段 → 淬冷段 → 冷却段 → 完成；完成即终态，刷新或重复点击都不得回到加热段 */
    @Transactional
    public FurnaceVO advance(Long id) {
        TemperingBatch batch = temperingRepository.findLockedById(id)
                .orElseThrow(() -> new BizException("钢化批次不存在：" + id));
        String stage = batch.getStage() == null ? "HEATING" : batch.getStage();
        if (batch.isFinished()) {
            throw new ConflictException(batch.getLockReason());
        }
        String next = switch (stage) {
            case "HEATING" -> "QUENCH";
            case "QUENCH" -> "COOLING";
            case "COOLING" -> "DONE";
            default -> throw new BizException("钢化批次 " + batch.getBatchNo()
                    + " 当前炉段标识非法：" + stage);
        };
        batch.setStage(next);
        temperingRepository.save(batch);
        FurnaceVO vo = toVO(batch);
        if ("DONE".equals(next) && batch.isAwaitingBackfill()) {
            vo.setNotice("炉程已全部走完，但尚未登记出炉片数，请在右侧补录一次出炉结果");
        }
        return vo;
    }

    /**
     * 出炉登记：出炉结果全生命周期只允许首次确认一次。
     * <ul>
     *   <li>在炉批次：首次确认出炉片数 / 破碎片数，阶段置完成并锁定；</li>
     *   <li>阶段已完成但片数缺失的历史批次：按真实完成批次补录一次，补录后锁定；</li>
     *   <li>已有有效出炉结果的完成批次：保留原结果。再次提交相同片数直接说明；
     *       提交不同片数抛 409 冲突，绝不能改写合格片数与破碎率。</li>
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

        FurnaceVO vo = toVO(batch);
        if (batch.isResultLocked()) {
            int oldBroken = batch.getBrokenCount() == null ? 0 : batch.getBrokenCount();
            boolean same = batch.getOutputCount() == outputCount && oldBroken == broken;
            String detail = "原出炉结果：出炉 " + batch.getOutputCount() + " 片、破碎 " + oldBroken
                    + " 片、合格 " + batch.getGoodCount() + " 片、破碎率 " + batch.getBrokenRate() + "%";
            if (same) {
                // 原结果原样保留，仅把保留原因说明返回，不触碰任何片数
                vo.setNotice("该批次出炉结果已确认，本次提交与首次结果一致，原结果已保留：" + detail);
                return vo;
            }
            throw new ConflictException("该批次已完成，出炉结果只允许首次确认一次。"
                    + "本次提交（出炉 " + outputCount + " 片、破碎 " + broken + " 片）与原结果不一致，"
                    + detail + "，原结果保留不变，不允许改写合格片数与破碎率");
        }

        boolean backfill = batch.isAwaitingBackfill();
        batch.setOutputCount(outputCount);
        batch.setBrokenCount(broken);
        batch.setStage("DONE");
        temperingRepository.save(batch);
        vo = toVO(batch);
        vo.setNotice(backfill
                ? "历史完成批次的出炉结果已补录（出炉 " + outputCount + " 片、破碎 " + broken
                        + " 片），补录后结果锁定，不再接受重复出炉"
                : "出炉结果已确认（出炉 " + outputCount + " 片、破碎 " + broken
                        + " 片），批次完成并锁定");
        return vo;
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
        vo.setFinished(batch.isFinished());
        vo.setResultLocked(batch.isResultLocked());
        vo.setAwaitingBackfill(batch.isAwaitingBackfill());
        vo.setCanDischarge(batch.canDischarge());
        vo.setCanAdvance(batch.canAdvance());
        vo.setLockReason(batch.getLockReason());
        vo.setOperator(batch.getOperator());

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
