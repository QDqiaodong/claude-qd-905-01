package com.glass.works.dto;

import com.glass.works.common.Dict;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 按深加工工序类型统计的投产情况。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessStatDTO {

    private String processType;
    private String processTypeName;
    private long recordCount;
    private long pieceCount;
    private long passCount;

    public ProcessStatDTO(String processType, Long recordCount, Long pieceCount, Long passCount) {
        this(processType, Dict.processTypeName(processType),
                recordCount == null ? 0L : recordCount,
                pieceCount == null ? 0L : pieceCount,
                passCount == null ? 0L : passCount);
    }

    public ProcessStatDTO(String processType, Long recordCount, Integer pieceCount, Integer passCount) {
        this(processType, recordCount,
                pieceCount == null ? null : pieceCount.longValue(),
                passCount == null ? null : passCount.longValue());
    }

    public ProcessStatDTO(String processType, Long recordCount, BigDecimal pieceCount, BigDecimal passCount) {
        this(processType, recordCount,
                pieceCount == null ? null : pieceCount.longValue(),
                passCount == null ? null : passCount.longValue());
    }

    /** 合格率 % */
    public double getPassRate() {
        return recordCount <= 0 ? 0.0 : Math.round(passCount * 1000.0 / recordCount) / 10.0;
    }
}
