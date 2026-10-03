package com.glass.works.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 按钢化炉号统计的出炉 / 破碎 / 平均加热温度。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FurnaceStatDTO {

    private String furnaceNo;
    private long batchCount;
    private long outputCount;
    private long brokenCount;
    private double avgHeatTemp;

    public FurnaceStatDTO(String furnaceNo, Long batchCount, Long outputCount, Long brokenCount, Double avgHeatTemp) {
        this(furnaceNo, batchCount == null ? 0L : batchCount,
                outputCount == null ? 0L : outputCount,
                brokenCount == null ? 0L : brokenCount,
                avgHeatTemp == null ? 0.0 : Math.round(avgHeatTemp * 10.0) / 10.0);
    }

    public FurnaceStatDTO(String furnaceNo, Long batchCount, Integer outputCount,
                          Integer brokenCount, BigDecimal avgHeatTemp) {
        this(furnaceNo, batchCount,
                outputCount == null ? null : outputCount.longValue(),
                brokenCount == null ? null : brokenCount.longValue(),
                avgHeatTemp == null ? null : avgHeatTemp.doubleValue());
    }

    public FurnaceStatDTO(String furnaceNo, Long batchCount, Long outputCount,
                          Long brokenCount, BigDecimal avgHeatTemp) {
        this(furnaceNo, batchCount, outputCount, brokenCount,
                avgHeatTemp == null ? null : avgHeatTemp.doubleValue());
    }

    public FurnaceStatDTO(String furnaceNo, Long batchCount, Integer outputCount,
                          Integer brokenCount, Double avgHeatTemp) {
        this(furnaceNo, batchCount,
                outputCount == null ? null : outputCount.longValue(),
                brokenCount == null ? null : brokenCount.longValue(),
                avgHeatTemp);
    }

    /** 破碎率 % */
    public double getBrokenRate() {
        return outputCount <= 0 ? 0.0 : Math.round(brokenCount * 1000.0 / outputCount) / 10.0;
    }
}
