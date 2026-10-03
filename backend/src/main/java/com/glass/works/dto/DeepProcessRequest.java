package com.glass.works.dto;

import java.math.BigDecimal;
import lombok.Data;

/** 深加工（镀膜 / 中空 / 夹胶）工序记录入参 */
@Data
public class DeepProcessRequest {

    private String recordNo;
    private String processType;
    private String batchNo;
    private String glassType;
    private Integer thicknessMm;
    private Integer pieceCount;

    // 镀膜
    private String filmSystem;
    private String targetMaterial;
    private Integer filmThicknessNm;
    private BigDecimal emissivity;

    // 中空
    private String spacer;
    private String gasType;
    private Integer dewPoint;

    // 夹胶
    private BigDecimal pvbThickness;
    private Integer autoclaveTemp;
    private BigDecimal autoclavePressure;
    private Integer autoclaveMinutes;

    private String operator;
    private String remark;
}
