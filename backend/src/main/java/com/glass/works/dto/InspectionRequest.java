package com.glass.works.dto;

import java.math.BigDecimal;
import lombok.Data;

/** 检验记录入参 */
@Data
public class InspectionRequest {

    private String inspectNo;
    private String processType;
    private String batchNo;
    private String orderNo;
    private String defectType;
    private BigDecimal defectSizeMm;
    /** 缺陷横向相对位置 0~100 */
    private BigDecimal posX;
    /** 缺陷纵向相对位置 0~100 */
    private BigDecimal posY;
    private String verdict;
    private String inspector;
    private Integer sheetCount;
}
