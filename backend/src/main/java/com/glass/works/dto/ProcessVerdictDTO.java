package com.glass.works.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按工序统计合格 / 返修 / 报废数量。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessVerdictDTO {

    private String processType;
    private String processTypeName;
    private long total;
    private long passCount;
    private long reworkCount;
    private long scrapCount;

    public ProcessVerdictDTO(String processType, Long total, Long passCount, Long reworkCount, Long scrapCount) {
        this(processType, com.glass.works.common.Dict.workStageName(processType),
                total == null ? 0L : total,
                passCount == null ? 0L : passCount,
                reworkCount == null ? 0L : reworkCount,
                scrapCount == null ? 0L : scrapCount);
    }

    public ProcessVerdictDTO(String processType, Long total, Integer passCount,
                             Integer reworkCount, Integer scrapCount) {
        this(processType, total,
                passCount == null ? null : passCount.longValue(),
                reworkCount == null ? null : reworkCount.longValue(),
                scrapCount == null ? null : scrapCount.longValue());
    }

    public ProcessVerdictDTO(String processType, Long total, BigDecimal passCount,
                             BigDecimal reworkCount, BigDecimal scrapCount) {
        this(processType, total,
                passCount == null ? null : passCount.longValue(),
                reworkCount == null ? null : reworkCount.longValue(),
                scrapCount == null ? null : scrapCount.longValue());
    }

    /** 合格率 % */
    public double getPassRate() {
        return total <= 0 ? 0.0 : Math.round(passCount * 1000.0 / total) / 10.0;
    }
}
