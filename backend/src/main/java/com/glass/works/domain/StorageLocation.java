package com.glass.works.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Getter;
import lombok.Setter;

/**
 * 库位：区 → 架 → 层 三层立体货位，用于原片立体图展示与承重校验。
 */
@Entity
@Table(name = "storage_location")
@Getter
@Setter
public class StorageLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 区编码 A / B / C */
    @Column(name = "zone_code", nullable = false, length = 8)
    private String zoneCode;

    @Column(name = "zone_name", length = 32)
    private String zoneName;

    /** 架号 A-01 */
    @Column(name = "rack_code", nullable = false, length = 16)
    private String rackCode;

    /** 层号，自下而上 1~5 */
    @Column(name = "level_no", nullable = false)
    private Integer levelNo;

    /** 该层存放的批次数 */
    @Column(name = "batch_count")
    private Integer batchCount;

    /** 该层存放的片数 */
    @Column(name = "sheet_count")
    private Integer sheetCount;

    /** 承重上限 kg */
    @Column(name = "max_load_kg", precision = 12, scale = 2)
    private BigDecimal maxLoadKg;

    /** 当前载重 kg */
    @Column(name = "current_load_kg", precision = 12, scale = 2)
    private BigDecimal currentLoadKg;

    @Column(name = "note", length = 64)
    private String note;

    /** 剩余承重 kg（前端画承载条用） */
    @Transient
    public BigDecimal getRemainingLoadKg() {
        BigDecimal max = maxLoadKg == null ? BigDecimal.ZERO : maxLoadKg;
        BigDecimal cur = currentLoadKg == null ? BigDecimal.ZERO : currentLoadKg;
        return max.subtract(cur).setScale(2, RoundingMode.HALF_UP);
    }

    /** 载荷率 % */
    @Transient
    public Double getLoadRate() {
        if (maxLoadKg == null || maxLoadKg.doubleValue() <= 0) {
            return 0.0;
        }
        double cur = currentLoadKg == null ? 0 : currentLoadKg.doubleValue();
        return Math.round(cur / maxLoadKg.doubleValue() * 1000.0) / 10.0;
    }
}
