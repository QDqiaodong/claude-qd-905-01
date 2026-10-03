package com.glass.works.dto;

import com.glass.works.common.Dict;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 按磨边边型统计的加工量与崩边量。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EdgeStatDTO {

    private String edgeType;
    private String edgeTypeName;
    private long recordCount;
    private long pieceCount;
    private long chipCount;
    private long chipRecordCount;

    public EdgeStatDTO(String edgeType, Long recordCount, Long pieceCount, Long chipCount, Long chipRecordCount) {
        this(edgeType, Dict.edgeTypeName(edgeType),
                recordCount == null ? 0L : recordCount,
                pieceCount == null ? 0L : pieceCount,
                chipCount == null ? 0L : chipCount,
                chipRecordCount == null ? 0L : chipRecordCount);
    }

    public EdgeStatDTO(String edgeType, Long recordCount, Integer pieceCount,
                       Integer chipCount, Integer chipRecordCount) {
        this(edgeType, recordCount,
                pieceCount == null ? null : pieceCount.longValue(),
                chipCount == null ? null : chipCount.longValue(),
                chipRecordCount == null ? null : chipRecordCount.longValue());
    }

    public EdgeStatDTO(String edgeType, Long recordCount, BigDecimal pieceCount,
                       BigDecimal chipCount, BigDecimal chipRecordCount) {
        this(edgeType, recordCount,
                pieceCount == null ? null : pieceCount.longValue(),
                chipCount == null ? null : chipCount.longValue(),
                chipRecordCount == null ? null : chipRecordCount.longValue());
    }

    /** 崩边率 ‰（千分比） */
    public double getChipRate() {
        return pieceCount <= 0 ? 0.0 : Math.round(chipCount * 1000.0 / pieceCount) / 10.0;
    }
}
