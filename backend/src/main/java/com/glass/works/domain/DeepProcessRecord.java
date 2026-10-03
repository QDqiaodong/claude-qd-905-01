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
 * 深加工工序记录：镀膜 / 中空 / 夹胶三类工序各自的参数。
 * 三类参数放在一张表，按 process_type 区分使用哪一段。
 */
@Entity
@Table(name = "deep_process_record")
@Getter
@Setter
public class DeepProcessRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_no", nullable = false, unique = true, length = 32)
    private String recordNo;

    /** COATING 镀膜 / INSULATING 中空 / LAMINATING 夹胶 */
    @Column(name = "process_type", nullable = false, length = 16)
    private String processType;

    @Column(name = "batch_no", length = 32)
    private String batchNo;

    @Column(name = "glass_type", length = 32)
    private String glassType;

    @Column(name = "thickness_mm")
    private Integer thicknessMm;

    @Column(name = "piece_count")
    private Integer pieceCount;

    // ---------------- 镀膜 ----------------
    /** 膜系 */
    @Column(name = "film_system", length = 32)
    private String filmSystem;

    /** 靶材 */
    @Column(name = "target_material", length = 32)
    private String targetMaterial;

    /** 膜层厚度 nm */
    @Column(name = "film_thickness_nm")
    private Integer filmThicknessNm;

    /** 辐射率 */
    @Column(name = "emissivity", precision = 5, scale = 3)
    private BigDecimal emissivity;

    // ---------------- 中空 ----------------
    /** 间隔条 */
    @Column(name = "spacer", length = 32)
    private String spacer;

    /** 气体：AIR 空气 / ARGON 氩气 / KRYPTON 氪气 */
    @Column(name = "gas_type", length = 16)
    private String gasType;

    /** 露点 ℃ */
    @Column(name = "dew_point")
    private Integer dewPoint;

    // ---------------- 夹胶 ----------------
    /** PVB 厚度 mm */
    @Column(name = "pvb_thickness", precision = 4, scale = 2)
    private BigDecimal pvbThickness;

    /** 高压釜温度 ℃ */
    @Column(name = "autoclave_temp")
    private Integer autoclaveTemp;

    /** 高压釜压力 MPa */
    @Column(name = "autoclave_pressure", precision = 4, scale = 2)
    private BigDecimal autoclavePressure;

    /** 高压釜时长 min */
    @Column(name = "autoclave_minutes")
    private Integer autoclaveMinutes;

    // ---------------- 结果 ----------------
    /** PASS 合格 / FAIL 不合格 */
    @Column(name = "result", length = 8)
    private String result;

    @Column(name = "remark", length = 128)
    private String remark;

    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 中空：露点高于 -40℃ 判不合格 */
    @Transient
    public boolean isDewPointOk() {
        return dewPoint == null || dewPoint <= -40;
    }

    @Transient
    public String getResultName() {
        return "PASS".equals(result) ? "合格" : "不合格";
    }
}
