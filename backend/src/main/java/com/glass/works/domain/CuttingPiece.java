package com.glass.works.domain;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
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
 * 切割出片规格明细：一张切割单下的若干出片规格与数量。
 *
 * <p>与 {@link CuttingOrder} 一样使用混合访问：{@code area_sqm} 列由
 * 长 × 宽 × 数量 派生，走 {@code @Access(PROPERTY)}，
 * setter 反解出「数量」——库里存的是派生值，读回来时按规格还原片数。</p>
 */
@Entity
@Table(name = "cutting_piece")
@Access(AccessType.FIELD)
@Getter
@Setter
public class CuttingPiece {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cutting_order_id", nullable = false)
    private Long cuttingOrderId;

    @Column(name = "sort_no")
    private Integer sortNo;

    @Column(name = "length_mm")
    private Integer lengthMm;

    @Column(name = "width_mm")
    private Integer widthMm;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "remark", length = 64)
    private String remark;

    /** 派生列（面积 ㎡），走属性访问 */
    private BigDecimal areaSqm;

    @Access(AccessType.PROPERTY)
    @Column(name = "area_sqm", precision = 12, scale = 4)
    public BigDecimal getAreaSqm() {
        if (lengthMm == null || widthMm == null || quantity == null) {
            return areaSqm;
        }
        double area = lengthMm.doubleValue() * widthMm.doubleValue() * quantity / 1_000_000.0;
        return BigDecimal.valueOf(area).setScale(4, RoundingMode.HALF_UP);
    }

    public void setAreaSqm(BigDecimal value) {
        this.areaSqm = value;
    }

    /** 单片面积 ㎡ */
    @Transient
    public Double getSingleAreaSqm() {
        if (lengthMm == null || widthMm == null) {
            return 0.0;
        }
        return Math.round(lengthMm.doubleValue() * widthMm.doubleValue() / 1_000_000.0 * 10000.0) / 10000.0;
    }

    @Transient
    public String getSpecText() {
        return (lengthMm == null ? "0" : lengthMm) + "×" + (widthMm == null ? "0" : widthMm);
    }
}
