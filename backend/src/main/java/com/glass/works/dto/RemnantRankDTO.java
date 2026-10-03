package com.glass.works.dto;

import com.glass.works.common.Dict;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 原片余料排行：哪张切割单剩下的余料最多（可直接二次利用）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemnantRankDTO {

    private String orderNo;
    private String batchNo;
    private String glassType;
    private String glassTypeName;
    private BigDecimal remnantAreaSqm;
    private BigDecimal utilizationRate;

    public RemnantRankDTO(String orderNo, String batchNo, String glassType,
                          BigDecimal remnantAreaSqm, BigDecimal utilizationRate) {
        this(orderNo, batchNo, glassType, Dict.glassTypeName(glassType), remnantAreaSqm, utilizationRate);
    }
}
