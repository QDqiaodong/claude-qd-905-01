package com.glass.works.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 检验记录：某个工序环节上发现的一处缺陷及其判定。
 * posX / posY 为缺陷在玻璃板上的相对坐标（0~100，百分比），供缺陷分布图打点。
 */
@Entity
@Table(name = "inspection_record")
@Getter
@Setter
public class InspectionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inspect_no", nullable = false, unique = true, length = 32)
    private String inspectNo;

    /** 工序环节：CUTTING / GRINDING / TEMPERING / COATING / INSULATING / LAMINATING */
    @Column(name = "process_type", nullable = false, length = 16)
    private String processType;

    @Column(name = "batch_no", length = 32)
    private String batchNo;

    @Column(name = "order_no", length = 32)
    private String orderNo;

    /** 缺陷类型：SCRATCH 划伤 / BUBBLE 气泡 / STONE 结石 / CHIP 崩边 / WAVE 波形 / STRESS 应力斑 */
    @Column(name = "defect_type", length = 16)
    private String defectType;

    /** 缺陷尺寸 mm */
    @Column(name = "defect_size_mm", precision = 6, scale = 2)
    private BigDecimal defectSizeMm;

    /** 缺陷横向相对位置 0~100 */
    @Column(name = "pos_x", precision = 5, scale = 2)
    private BigDecimal posX;

    /** 缺陷纵向相对位置 0~100 */
    @Column(name = "pos_y", precision = 5, scale = 2)
    private BigDecimal posY;

    /** 判定：PASS 合格 / REWORK 返修 / SCRAP 报废 */
    @Column(name = "verdict", length = 8)
    private String verdict;

    @Column(name = "inspector", length = 32)
    private String inspector;

    /** 该判定涉及片数 */
    @Column(name = "sheet_count")
    private Integer sheetCount;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;

    /** 判返修后关联的返修批次（可追溯闭环；非返修判定为空） */
    @Column(name = "rework_id")
    private Long reworkId;

    @Transient
    public String getVerdictName() {
        return switch (verdict == null ? "" : verdict) {
            case "PASS" -> "合格";
            case "REWORK" -> "返修";
            case "SCRAP" -> "报废";
            default -> verdict == null ? "" : verdict;
        };
    }
}
