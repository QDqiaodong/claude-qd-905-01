package com.glass.works.domain;

import com.glass.works.common.Dict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 返修时间线事件：返修批次每一次状态流转、并发冲突与发运拦截的审计记录。
 * 批次时间线按 id 升序回放，全过程可追溯。
 */
@Entity
@Table(name = "rework_event")
@Getter
@Setter
public class ReworkEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属返修批次 */
    @Column(name = "rework_id", nullable = false)
    private Long reworkId;

    /**
     * CREATE 判返修隔离 / MERGE 历史归并建批 / APPEND 并入在制批次 /
     * DISPOSE 登记处置 / SUBMIT 提交复检 / RECHECK 复检判定 /
     * CONFLICT 并发冲突 / SHIP_BLOCK 发运拦截
     */
    @Column(name = "event_type", nullable = false, length = 16)
    private String eventType;

    @Column(name = "from_status", length = 16)
    private String fromStatus;

    @Column(name = "to_status", length = 16)
    private String toStatus;

    /** 复检结论：PASS / REWORK（仅 RECHECK、CONFLICT 事件有值） */
    @Column(name = "verdict", length = 8)
    private String verdict;

    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Transient
    public String getEventTypeName() {
        return Dict.reworkEventName(eventType);
    }

    @Transient
    public String getFromStatusName() {
        return fromStatus == null ? "" : Dict.reworkStatusName(fromStatus);
    }

    @Transient
    public String getToStatusName() {
        return toStatus == null ? "" : Dict.reworkStatusName(toStatus);
    }

    @Transient
    public String getVerdictName() {
        return verdict == null ? "" : Dict.verdictName(verdict);
    }
}
