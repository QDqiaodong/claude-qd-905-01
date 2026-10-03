package com.glass.works.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;

/** 原片批次新增 / 修改入参 */
@Data
public class StockRequest {

    private String batchNo;
    private String glassType;
    private Integer thicknessMm;
    private Integer lengthMm;
    private Integer widthMm;
    private Integer sheetCount;
    private String supplier;
    private BigDecimal transmittance;
    private LocalDate inboundDate;
    private Long locationId;
}
