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
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 钢化炉批次：一炉玻璃的加热 / 淬冷 / 冷却全过程参数。
 *
 * <p>派生属性 {@code temp_range}（温度区间串，如 “660-700”）走 {@code @Access(PROPERTY)}：
 * getter 把 tempLow / tempHigh 两个字段拼成区间串，setter 反拆回两个字段。</p>
 */
@Entity
@Table(name = "tempering_batch")
@Access(AccessType.FIELD)
@Getter
@Setter
public class TemperingBatch {

    private static final Pattern RANGE_PATTERN = Pattern.compile(
            "^\\s*(\\d+(?:\\.\\d+)?)\\s*-\\s*(\\d+(?:\\.\\d+)?)\\s*$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_no", nullable = false, unique = true, length = 32)
    private String batchNo;

    /** 炉号 */
    @Column(name = "furnace_no", length = 16)
    private String furnaceNo;

    @Column(name = "glass_type", length = 32)
    private String glassType;

    @Column(name = "thickness_mm")
    private Integer thicknessMm;

    /** 工艺温控下限 ℃ */
    @Column(name = "temp_low")
    private Integer tempLow;

    /** 工艺温控上限 ℃ */
    @Column(name = "temp_high")
    private Integer tempHigh;

    /** 实际加热温度 ℃ */
    @Column(name = "heat_temp")
    private Integer heatTemp;

    /** 加热时长 s */
    @Column(name = "heat_seconds")
    private Integer heatSeconds;

    /** 淬冷风压 kPa */
    @Column(name = "quench_pressure", precision = 5, scale = 2)
    private BigDecimal quenchPressure;

    /** 波形度 mm/m */
    @Column(name = "warpage", precision = 5, scale = 3)
    private BigDecimal warpage;

    /** 颗粒度（50×50mm 内颗粒数） */
    @Column(name = "particle_count")
    private Integer particleCount;

    /** 出炉片数 */
    @Column(name = "output_count")
    private Integer outputCount;

    /** 破碎片数 */
    @Column(name = "broken_count")
    private Integer brokenCount;

    /** 当前炉段：HEATING / QUENCH / COOLING / DONE */
    @Column(name = "stage", length = 16)
    private String stage;

    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 派生列（温度区间串），走属性访问 */
    private String tempRange;

    @Access(AccessType.PROPERTY)
    @Column(name = "temp_range", length = 24)
    public String getTempRange() {
        if (tempLow == null || tempHigh == null) {
            return tempRange;
        }
        return tempLow + "-" + tempHigh;
    }

    public void setTempRange(String value) {
        if (value == null || value.isBlank()) {
            this.tempRange = value;
            return;
        }
        Matcher matcher = RANGE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new BizException("钢化温控区间串格式非法（应为 660-700）：" + value);
        }
        this.tempLow = (int) Math.round(Double.parseDouble(matcher.group(1)));
        this.tempHigh = (int) Math.round(Double.parseDouble(matcher.group(2)));
        this.tempRange = value.trim();
    }

    /** 破碎率 % */
    @Transient
    public Double getBrokenRate() {
        if (outputCount == null || outputCount <= 0 || brokenCount == null) {
            return 0.0;
        }
        return Math.round(brokenCount.doubleValue() / outputCount * 1000.0) / 10.0;
    }

    @Transient
    public Integer getGoodCount() {
        int out = outputCount == null ? 0 : outputCount;
        int broken = brokenCount == null ? 0 : brokenCount;
        return out - broken;
    }

    /** 加热时长（分钟，前端隧道标注用） */
    @Transient
    public BigDecimal getHeatMinutes() {
        int sec = heatSeconds == null ? 0 : heatSeconds;
        return BigDecimal.valueOf(sec / 60.0).setScale(1, RoundingMode.HALF_UP);
    }
}
