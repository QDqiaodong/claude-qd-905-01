package com.glass.works.repository;

import com.glass.works.domain.ReworkEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReworkEventRepository extends JpaRepository<ReworkEvent, Long> {

    /** 批次时间线：按 id 升序回放 */
    List<ReworkEvent> findByReworkIdOrderByIdAsc(Long reworkId);

    /** 最近一次某类事件（并发冲突提示里要指出先生效的结论） */
    Optional<ReworkEvent> findTopByReworkIdAndEventTypeOrderByIdDesc(Long reworkId, String eventType);
}
