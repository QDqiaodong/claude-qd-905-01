package com.glass.works.dto;

import com.glass.works.common.Dict;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按玻璃种类统计的产量与面积（仓库报产用）。
 * 显式提供多套构造器，兼容 JPQL 聚合的不同返回类型（Long / BigDecimal / Double / Integer）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GlassTypeStatDTO {

    private String glassType;
    private String glassTypeName;
    private long batchCount;
    private long sheetCount;
    private double areaSqm;

    /** JPQL 投影：种类、批次数、片数、面积（面积已含计算，直接给 Double） */
    public GlassTypeStatDTO(String glassType, Long batchCount, Long sheetCount, Double areaSqm) {
        this(glassType, Dict.glassTypeName(glassType),
                batchCount == null ? 0L : batchCount,
                sheetCount == null ? 0L : sheetCount,
                areaSqm == null ? 0.0 : areaSqm);
    }

    public GlassTypeStatDTO(String glassType, Long batchCount, Long sheetCount, BigDecimal areaSqm) {
        this(glassType, batchCount, sheetCount, areaSqm == null ? 0.0 : areaSqm.doubleValue());
    }

    public GlassTypeStatDTO(String glassType, Long batchCount, Integer sheetCount, BigDecimal areaSqm) {
        this(glassType, batchCount, sheetCount == null ? null : sheetCount.longValue(), areaSqm);
    }

    public GlassTypeStatDTO(String glassType, Integer batchCount, Integer sheetCount, BigDecimal areaSqm) {
        this(glassType, batchCount == null ? null : batchCount.longValue(),
                sheetCount == null ? null : sheetCount.longValue(), areaSqm);
    }
}
