package com.glass.works.domain;

import com.glass.works.common.Dict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 返修批次：任一工序判返修后形成的隔离闭环单，是检验、深加工与成品架共享的批次质量状态。
 *
 * <p>归并口径：同一来源工序下，批次号 / 订单号任一标识相同（或经同一张检验单上的
 * 批次↔订单桥接确认指向同一实物）的历史记录归并为同一返修批次；
 * 两个标识都没有的记录各自单独成批，宁缺毋滥，不误并不同实物。</p>
 *
 * <p>受控流转：ISOLATED 隔离 → PROCESSING 处理中 → PENDING_RECHECK 待复检 → RELEASED 已放行；
 * 复检再判返修回到 ISOLATED。只有 RELEASED 才允许成品架上架 / 发运。</p>
 */
@Entity
@Table(name = "rework_batch")
@Getter
@Setter
public class ReworkBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 返修单号 RW-xxxxxxxx-### */
    @Column(name = "rework_no", nullable = false, unique = true, length = 32)
    private String reworkNo;

    /** 来源工序：CUTTING / GRINDING / TEMPERING / COATING / INSULATING / LAMINATING */
    @Column(name = "process_type", nullable = false, length = 16)
    private String processType;

    /** 原片批次号（可空：历史记录可能只有订单号） */
    @Column(name = "batch_no", length = 32)
    private String batchNo;

    /** 关联单号（可空：历史记录可能只有批次号） */
    @Column(name = "order_no", length = 32)
    private String orderNo;

    /** 缺陷汇总（归并组内各记录的缺陷类型名） */
    @Column(name = "defect_summary", length = 128)
    private String defectSummary;

    /** 涉及片数合计 */
    @Column(name = "sheet_count")
    private Integer sheetCount;

    /** ISOLATED 隔离 / PROCESSING 处理中 / PENDING_RECHECK 待复检 / RELEASED 已放行 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 最近经办人 */
    @Column(name = "operator", length = 32)
    private String operator;

    /** 归并说明 / 最新进展备注 */
    @Column(name = "note", length = 255)
    private String note;

    /** 乐观锁版本号：配合行级悲观锁，保证并发结论只生效一个 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    public String getStatusName() {
        return Dict.reworkStatusName(status);
    }

    @Transient
    public String getProcessTypeName() {
        return Dict.workStageName(processType);
    }
}
