package com.glass.works.dto;

import com.glass.works.common.Dict;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 按发货状态统计的成品架情况。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RackStatDTO {

    private String shipStatus;
    private String shipStatusName;
    private long rackCount;
    private long sheetCount;
    private double areaSqm;

    public RackStatDTO(String shipStatus, Long rackCount, Long sheetCount, Double areaSqm) {
        this(shipStatus, Dict.shipStatusName(shipStatus),
                rackCount == null ? 0L : rackCount,
                sheetCount == null ? 0L : sheetCount,
                areaSqm == null ? 0.0 : Math.round(areaSqm * 100.0) / 100.0);
    }

    public RackStatDTO(String shipStatus, Long rackCount, Integer sheetCount, BigDecimal areaSqm) {
        this(shipStatus, rackCount,
                sheetCount == null ? null : sheetCount.longValue(),
                areaSqm == null ? null : areaSqm.doubleValue());
    }

    public RackStatDTO(String shipStatus, Long rackCount, Long sheetCount, BigDecimal areaSqm) {
        this(shipStatus, rackCount, sheetCount, areaSqm == null ? null : areaSqm.doubleValue());
    }
}
