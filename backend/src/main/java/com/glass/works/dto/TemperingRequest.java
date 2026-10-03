package com.glass.works.dto;

import java.math.BigDecimal;
import lombok.Data;

/** 钢化炉批次入参 */
@Data
public class TemperingRequest {

    private String batchNo;
    private String furnaceNo;
    private String glassType;
    private Integer thicknessMm;
    /** 工艺温控下限 */
    private Integer tempLow;
    /** 工艺温控上限 */
    private Integer tempHigh;
    /** 实际加热温度 */
    private Integer heatTemp;
    private Integer heatSeconds;
    private BigDecimal quenchPressure;
    private BigDecimal warpage;
    private Integer particleCount;
    private Integer outputCount;
    private Integer brokenCount;
    private String operator;
    private Integer pieceCount;
}
