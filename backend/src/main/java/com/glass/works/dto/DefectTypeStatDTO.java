package com.glass.works.dto;

import com.glass.works.common.Dict;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缺陷类型占比统计：某类缺陷出现了多少次、平均缺陷尺寸多大。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DefectTypeStatDTO {

    private String defectType;
    private String defectTypeName;
    private String color;
    private long count;
    private double avgSizeMm;
    private long sheetCount;

    public DefectTypeStatDTO(String defectType, Long count, Double avgSizeMm, Long sheetCount) {
        this(defectType, Dict.defectTypeName(defectType), Dict.defectTypeColor(defectType),
                count == null ? 0L : count,
                avgSizeMm == null ? 0.0 : Math.round(avgSizeMm * 100.0) / 100.0,
                sheetCount == null ? 0L : sheetCount);
    }

    public DefectTypeStatDTO(String defectType, Long count, BigDecimal avgSizeMm, Long sheetCount) {
        this(defectType, count, avgSizeMm == null ? 0.0 : avgSizeMm.doubleValue(), sheetCount);
    }

    public DefectTypeStatDTO(String defectType, Long count, BigDecimal avgSizeMm, Integer sheetCount) {
        this(defectType, count, avgSizeMm, sheetCount == null ? null : sheetCount.longValue());
    }

    public DefectTypeStatDTO(String defectType, Long count, Double avgSizeMm, Integer sheetCount) {
        this(defectType, count, avgSizeMm, sheetCount == null ? null : sheetCount.longValue());
    }
}
