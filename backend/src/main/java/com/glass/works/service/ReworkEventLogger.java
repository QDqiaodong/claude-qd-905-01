package com.glass.works.service;

import com.glass.works.domain.ReworkEvent;
import com.glass.works.repository.ReworkEventRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 返修时间线审计写入。
 *
 * <p>普通事件跟随业务事务（REQUIRED）：状态流转成功才落审计，失败一起回滚。</p>
 *
 * <p>并发冲突、发运拦截这类「业务本身要报错回滚、但拦截事实必须留痕」的事件，
 * 走 REQUIRES_NEW 独立事务提交，不随业务回滚而丢失，保证全过程可审计。</p>
 */
@Component
public class ReworkEventLogger {

    private final ReworkEventRepository eventRepository;

    public ReworkEventLogger(ReworkEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    /** 跟随当前业务事务的事件 */
    @Transactional(propagation = Propagation.REQUIRED)
    public ReworkEvent log(Long reworkId, String eventType, String fromStatus, String toStatus,
                           String verdict, String operator, String note) {
        return eventRepository.save(build(reworkId, eventType, fromStatus, toStatus, verdict, operator, note));
    }

    /** 独立事务提交的事件：业务回滚也保留（并发冲突、发运拦截） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReworkEvent logIndependent(Long reworkId, String eventType, String fromStatus, String toStatus,
                                      String verdict, String operator, String note) {
        return eventRepository.save(build(reworkId, eventType, fromStatus, toStatus, verdict, operator, note));
    }

    private ReworkEvent build(Long reworkId, String eventType, String fromStatus, String toStatus,
                              String verdict, String operator, String note) {
        ReworkEvent event = new ReworkEvent();
        event.setReworkId(reworkId);
        event.setEventType(eventType);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setVerdict(verdict);
        event.setOperator(operator);
        event.setNote(note != null && note.length() > 255 ? note.substring(0, 255) : note);
        event.setCreatedAt(LocalDateTime.now());
        return event;
    }
}
