package com.glass.works.domain;

import com.glass.works.common.BizException;
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
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 原片批次：一批同规格的玻璃原片。
 *
 * <p>本实体演示 JPA 混合访问（mixed access）：类级别 {@code @Access(FIELD)}，
 * 而 {@code spec_text} 列走 {@code @Access(PROPERTY)} —— 库里存的是
 * “6mm 2440×1830” 这种拼接串，getter 由厚度/长/宽实时拼出，
 * setter 反解析该串并把厚度、长、宽三个字段改写回去。</p>
 */
@Entity
@Table(name = "glass_stock")
@Access(AccessType.FIELD)
@Getter
@Setter
public class GlassStock {

    /** 规格串格式：6mm 2440×1830 */
    private static final Pattern SPEC_PATTERN = Pattern.compile(
            "^\\s*(\\d+(?:\\.\\d+)?)\\s*mm\\s*(\\d+(?:\\.\\d+)?)\\s*[×xX*]\\s*(\\d+(?:\\.\\d+)?)\\s*$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_no", nullable = false, unique = true, length = 32)
    private String batchNo;

    @Column(name = "glass_type", nullable = false, length = 32)
    private String glassType;

    @Column(name = "thickness_mm")
    private Integer thicknessMm;

    @Column(name = "length_mm")
    private Integer lengthMm;

    @Column(name = "width_mm")
    private Integer widthMm;

    @Column(name = "sheet_count")
    private Integer sheetCount;

    @Column(name = "supplier", length = 64)
    private String supplier;

    @Column(name = "transmittance", precision = 5, scale = 2)
    private BigDecimal transmittance;

    @Column(name = "inbound_date")
    private LocalDate inboundDate;

    @Column(name = "location_id")
    private Long locationId;

    /** 整批重量 kg（入库时按 2.5kg/(㎡·mm) 估算，成品架承重校验要用） */
    @Column(name = "weight_kg", precision = 12, scale = 2)
    private BigDecimal weightKg;

    /** 派生列，走属性访问；不要直接在字段上加 @Column */
    private String specText;

    @Access(AccessType.PROPERTY)
    @Column(name = "spec_text", length = 48)
    public String getSpecText() {
        if (thicknessMm == null || lengthMm == null || widthMm == null) {
            return specText;
        }
        return thicknessMm + "mm " + lengthMm + "×" + widthMm;
    }

    public void setSpecText(String value) {
        if (value == null || value.isBlank()) {
            this.specText = value;
            return;
        }
        Matcher matcher = SPEC_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new BizException("原片规格串格式非法（应为 6mm 2440×1830）：" + value);
        }
        this.thicknessMm = (int) Math.round(Double.parseDouble(matcher.group(1)));
        this.lengthMm = (int) Math.round(Double.parseDouble(matcher.group(2)));
        this.widthMm = (int) Math.round(Double.parseDouble(matcher.group(3)));
        this.specText = value.trim();
    }

    /** 单㎡面积 */
    @Transient
    public Double getSheetAreaSqm() {
        if (lengthMm == null || widthMm == null) {
            return 0.0;
        }
        return round2(lengthMm.doubleValue() * widthMm.doubleValue() / 1_000_000.0);
    }

    /** 整批总面积 ㎡ = 单㎡ × 片数 */
    @Transient
    public Double getTotalAreaSqm() {
        return round2(getSheetAreaSqm() * (sheetCount == null ? 0 : sheetCount));
    }

    /** 按 2.5kg/(㎡·mm) 反算整批重量 */
    @Transient
    public BigDecimal getEstimatedWeightKg() {
        return BigDecimal.valueOf(getTotalAreaSqm()
                        * (thicknessMm == null ? 0 : thicknessMm) * 2.5)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
