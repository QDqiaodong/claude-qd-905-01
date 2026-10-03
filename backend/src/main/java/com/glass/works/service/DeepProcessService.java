package com.glass.works.service;

import com.glass.works.common.BizException;
import com.glass.works.common.Dict;
import com.glass.works.domain.DeepProcessRecord;
import com.glass.works.dto.DeepProcessRequest;
import com.glass.works.dto.ProcessStatDTO;
import com.glass.works.repository.DeepProcessRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 深加工工序：镀膜 / 中空 / 夹胶 各自参数记录与投产校验。 */
@Service
public class DeepProcessService {

    private final DeepProcessRepository repository;

    public DeepProcessService(DeepProcessRepository repository) {
        this.repository = repository;
    }

    public List<DeepProcessRecord> list() {
        return repository.findAll();
    }

    public List<DeepProcessRecord> listByType(String processType) {
        return repository.findByProcessTypeOrderByIdDesc(processType);
    }

    public List<ProcessStatDTO> stat() {
        return repository.statByProcessType();
    }

    @Transactional
    public DeepProcessRecord create(DeepProcessRequest request) {
        if (!StringUtils.hasText(request.getRecordNo())) {
            throw new BizException("工序记录单号不能为空");
        }
        if (!Dict.isValidProcessType(request.getProcessType())) {
            throw new BizException("深加工工序类型非法：" + request.getProcessType());
        }
        if (repository.existsByRecordNo(request.getRecordNo())) {
            throw new BizException("工序记录单号已存在：" + request.getRecordNo());
        }
        DeepProcessRecord record = new DeepProcessRecord();
        record.setRecordNo(request.getRecordNo());
        record.setProcessType(request.getProcessType());
        record.setBatchNo(request.getBatchNo());
        record.setGlassType(request.getGlassType());
        record.setThicknessMm(request.getThicknessMm());
        record.setPieceCount(request.getPieceCount() == null ? 1 : request.getPieceCount());
        record.setOperator(request.getOperator());
        record.setCreatedAt(LocalDateTime.now());

        String remark;
        switch (request.getProcessType()) {
            case Dict.PROCESS_COATING -> {
                remark = applyCoating(record, request);
            }
            case Dict.PROCESS_INSULATING -> {
                remark = applyInsulating(record, request);
            }
            default -> {
                remark = applyLaminating(record, request);
            }
        }
        record.setRemark(remark == null ? request.getRemark() : remark);
        return repository.save(record);
    }

    // ---------------- 镀膜 ----------------

    private String applyCoating(DeepProcessRecord record, DeepProcessRequest request) {
        if (!StringUtils.hasText(request.getFilmSystem())) {
            throw new BizException("镀膜工序必须填写膜系");
        }
        if (request.getFilmThicknessNm() == null || request.getFilmThicknessNm() < 20
                || request.getFilmThicknessNm() > 500) {
            throw new BizException("膜层厚度 " + request.getFilmThicknessNm()
                    + "nm 不在允许区间（20~500nm）内");
        }
        if (request.getEmissivity() == null) {
            throw new BizException("镀膜工序必须填写辐射率");
        }
        double emissivity = request.getEmissivity().doubleValue();
        if (emissivity < 0.02 || emissivity > 0.90) {
            throw new BizException("辐射率 " + emissivity + " 不在允许区间（0.020~0.900）内");
        }
        record.setFilmSystem(request.getFilmSystem());
        record.setTargetMaterial(request.getTargetMaterial());
        record.setFilmThicknessNm(request.getFilmThicknessNm());
        record.setEmissivity(request.getEmissivity().setScale(3, RoundingMode.HALF_UP));
        record.setResult("PASS");
        return "膜系 " + request.getFilmSystem() + "，靶材 "
                + (request.getTargetMaterial() == null ? "-" : request.getTargetMaterial())
                + "，膜厚 " + request.getFilmThicknessNm() + "nm，辐射率 " + emissivity;
    }

    // ---------------- 中空 ----------------

    private String applyInsulating(DeepProcessRecord record, DeepProcessRequest request) {
        if (!Dict.isValidGas(request.getGasType())) {
            throw new BizException("中空气体类型非法：" + request.getGasType());
        }
        if (request.getDewPoint() == null) {
            throw new BizException("中空工序必须填写露点");
        }
        record.setSpacer(request.getSpacer());
        record.setGasType(request.getGasType());
        record.setDewPoint(request.getDewPoint());
        // 中空玻璃露点高于 -40℃ 判不合格
        boolean ok = request.getDewPoint() <= Dict.DEW_POINT_LIMIT;
        record.setResult(ok ? "PASS" : "FAIL");
        return "间隔条 " + (request.getSpacer() == null ? "-" : request.getSpacer())
                + "，充 " + Dict.gasTypeName(request.getGasType())
                + "，露点 " + request.getDewPoint() + "℃ → " + (ok ? "合格" : "不合格（高于 -40℃）");
    }

    // ---------------- 夹胶 ----------------

    private String applyLaminating(DeepProcessRecord record, DeepProcessRequest request) {
        if (request.getPvbThickness() == null) {
            throw new BizException("夹胶工序必须填写 PVB 厚度");
        }
        // PVB 与玻璃厚度不匹配不允许投产
        if (!Dict.isPvbMatched(request.getThicknessMm(), request.getPvbThickness().doubleValue())) {
            Set<Double> allowed = Dict.pvbAllowed(request.getThicknessMm());
            String allowedText = allowed.isEmpty() ? "无可匹配规格"
                    : allowed.stream().map(String::valueOf).collect(Collectors.joining(" / "));
            throw new BizException("PVB 厚度 " + request.getPvbThickness()
                    + "mm 与玻璃厚度 " + request.getThicknessMm() + "mm 不匹配，"
                    + "该厚度允许使用：" + allowedText + "mm");
        }
        if (request.getAutoclaveTemp() == null || request.getAutoclaveTemp() < Dict.AUTOCLAVE_TEMP_MIN
                || request.getAutoclaveTemp() > Dict.AUTOCLAVE_TEMP_MAX) {
            throw new BizException("高压釜温度 " + request.getAutoclaveTemp() + "℃ 不在允许区间（"
                    + (int) Dict.AUTOCLAVE_TEMP_MIN + "~" + (int) Dict.AUTOCLAVE_TEMP_MAX + "℃）内");
        }
        if (request.getAutoclavePressure() == null
                || request.getAutoclavePressure().doubleValue() < Dict.AUTOCLAVE_PRESSURE_MIN
                || request.getAutoclavePressure().doubleValue() > Dict.AUTOCLAVE_PRESSURE_MAX) {
            throw new BizException("高压釜压力 " + request.getAutoclavePressure() + "MPa 不在允许区间（"
                    + Dict.AUTOCLAVE_PRESSURE_MIN + "~" + Dict.AUTOCLAVE_PRESSURE_MAX + "MPa）内");
        }
        record.setPvbThickness(request.getPvbThickness().setScale(2, RoundingMode.HALF_UP));
        record.setAutoclaveTemp(request.getAutoclaveTemp());
        record.setAutoclavePressure(request.getAutoclavePressure().setScale(2, RoundingMode.HALF_UP));
        record.setAutoclaveMinutes(request.getAutoclaveMinutes());
        record.setResult("PASS");
        return "PVB " + request.getPvbThickness() + "mm，高压釜 "
                + request.getAutoclaveTemp() + "℃ / " + request.getAutoclavePressure()
                + "MPa / " + request.getAutoclaveMinutes() + "min";
    }
}
