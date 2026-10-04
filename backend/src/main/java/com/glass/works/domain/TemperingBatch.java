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

    /** 已走完整个炉程（加热 → 淬冷 → 冷却 → 完成），终态不可再推进 */
    @Transient
    public boolean isFinished() {
        return "DONE".equals(stage);
    }

    /**
     * 是否已有有效出炉结果：阶段已完成且登记过正的出炉片数。
     * 出炉结果全生命周期只允许首次确认一次，以此为「已锁定」判据。
     */
    @Transient
    public boolean hasValidResult() {
        return "DONE".equals(stage) && outputCount != null && outputCount > 0;
    }

    /** 出炉结果已确认并锁定：合格片数 / 破碎率只读，重复出炉一律不得改写 */
    @Transient
    public boolean isResultLocked() {
        return hasValidResult();
    }

    /**
     * 历史遗留：阶段已推进到完成、但出炉片数缺失（未做过有效出炉登记）。
     * 这是真实完成的批次、不是可重新生产的在炉批次，仅允许补录一次出炉结果。
     */
    @Transient
    public boolean isAwaitingBackfill() {
        return "DONE".equals(stage) && (outputCount == null || outputCount <= 0);
    }

    /** 出炉登记按钮是否可用：未完成的在炉批次登记，或完成但缺片数的历史批次补录一次 */
    @Transient
    public boolean canDischarge() {
        return !"DONE".equals(stage) || isAwaitingBackfill();
    }

    /** 推进按钮是否可用：完成（无论是否已补录）即终态只读 */
    @Transient
    public boolean canAdvance() {
        return !"DONE".equals(stage);
    }

    /** 不可继续操作的明确原因（页面 / 台账只读提示用；可操作时为 null） */
    @Transient
    public String getLockReason() {
        if (hasValidResult()) {
            return "该批次已完成并确认出炉结果（出炉 " + outputCount + " 片、破碎 "
                    + (brokenCount == null ? 0 : brokenCount) + " 片），结果已锁定，不可再推进或重新出炉";
        }
        if (isAwaitingBackfill()) {
            return "该批次炉程已完成但出炉片数缺失，仅允许补录一次出炉结果，不可重新推进生产";
        }
        return null;
    }

    /** 阶段中文名（台账列表直接序列化实体时前端展示用） */
    @Transient
    public String getStageName() {
        return switch (stage == null ? "" : stage) {
            case "HEATING" -> "加热段";
            case "QUENCH" -> "淬冷段";
            case "COOLING" -> "冷却段";
            case "DONE" -> "已出炉";
            default -> stage;
        };
    }
}
