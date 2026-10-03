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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 磨边记录：切割后的玻璃走磨边机加工边部。
 */
@Entity
@Table(name = "edge_grinding")
@Getter
@Setter
public class EdgeGrinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_no", nullable = false, unique = true, length = 32)
    private String recordNo;

    @Column(name = "cutting_order_id")
    private Long cuttingOrderId;

    @Column(name = "batch_no", length = 32)
    private String batchNo;

    /** 磨边机 */
    @Column(name = "machine", length = 48)
    private String machine;

    /** 边型：FLAT 直边 / ROUND 圆边 / BEVEL 斜边 / DUCK 鸭嘴边 */
    @Column(name = "edge_type", length = 16)
    private String edgeType;

    /** 磨轮型号 */
    @Column(name = "wheel_model", length = 32)
    private String wheelModel;

    /** 进给速度 m/min */
    @Column(name = "feed_speed", precision = 5, scale = 2)
    private BigDecimal feedSpeed;

    /** 加工片数 */
    @Column(name = "piece_count")
    private Integer pieceCount;

    /** 崩边片数 */
    @Column(name = "chip_count")
    private Integer chipCount;

    /** 崩边记录描述 */
    @Column(name = "chip_note", length = 128)
    private String chipNote;

    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "grind_time")
    private LocalDateTime grindTime;

    /** 崩边率 % */
    @Transient
    public Double getChipRate() {
        if (pieceCount == null || pieceCount <= 0 || chipCount == null) {
            return 0.0;
        }
        return Math.round(chipCount.doubleValue() / pieceCount * 1000.0) / 10.0;
    }

    @Transient
    public Integer getGoodCount() {
        int total = pieceCount == null ? 0 : pieceCount;
        int chip = chipCount == null ? 0 : chipCount;
        return total - chip;
    }

    @Transient
    public BigDecimal getFeedSpeedRounded() {
        return feedSpeed == null ? BigDecimal.ZERO : feedSpeed.setScale(2, RoundingMode.HALF_UP);
    }
}
