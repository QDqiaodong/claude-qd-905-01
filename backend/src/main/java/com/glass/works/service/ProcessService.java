package com.glass.works.service;

import com.glass.works.common.BizException;
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
        batch.setOutputCount(request.getOutputCount());
        batch.setBrokenCount(request.getBrokenCount() == null ? 0 : request.getBrokenCount());
        batch.setOperator(request.getOperator());
        batch.setStage("HEATING");
        batch.setCreatedAt(LocalDateTime.now());
        return temperingRepository.save(batch);
    }

    /** 只改工艺参数（滑块）并同步校验 */
    @Transactional
    public TemperingBatch updateTempering(Long id, TemperingRequest request) {
        TemperingBatch batch = temperingRepository.findById(id)
                .orElseThrow(() -> new BizException("钢化批次不存在：" + id));
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

    /** 炉内推进：加热段 → 淬冷段 → 冷却段 → 出炉 */
    @Transactional
    public FurnaceVO advance(Long id) {
        TemperingBatch batch = temperingRepository.findById(id)
                .orElseThrow(() -> new BizException("钢化批次不存在：" + id));
        String stage = batch.getStage() == null ? "HEATING" : batch.getStage();
        batch.setStage(switch (stage) {
            case "HEATING" -> "QUENCH";
            case "QUENCH" -> "COOLING";
            case "COOLING" -> "DONE";
            default -> "HEATING";
        });
        temperingRepository.save(batch);
        return toVO(batch);
    }

    /** 记录出炉结果：破碎片数不得超过出炉片数 */
    @Transactional
    public FurnaceVO discharge(Long id, Integer outputCount, Integer brokenCount) {
        TemperingBatch batch = temperingRepository.findById(id)
                .orElseThrow(() -> new BizException("钢化批次不存在：" + id));
        if (outputCount == null || outputCount <= 0) {
            throw new BizException("出炉片数必须为正整数");
        }
        int broken = brokenCount == null ? 0 : brokenCount;
        if (broken > outputCount) {
            throw new BizException("破碎片数 " + broken + " 不得超过出炉片数 " + outputCount);
        }
        batch.setOutputCount(outputCount);
        batch.setBrokenCount(broken);
        batch.setStage("DONE");
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
