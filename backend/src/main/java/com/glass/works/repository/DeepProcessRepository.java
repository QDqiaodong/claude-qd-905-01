package com.glass.works.repository;

import com.glass.works.domain.DeepProcessRecord;
import com.glass.works.dto.ProcessStatDTO;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeepProcessRepository extends JpaRepository<DeepProcessRecord, Long> {

    /**
     * 手写 JPQL ⑥：按深加工工序类型统计投产批次、片数与合格批次。
     */
    @Query("select new com.glass.works.dto.ProcessStatDTO("
            + "d.processType, count(d), sum(d.pieceCount), "
            + "sum(case when d.result = 'PASS' then 1 else 0 end)) "
            + "from DeepProcessRecord d "
            + "group by d.processType")
    List<ProcessStatDTO> statByProcessType();

    List<DeepProcessRecord> findByProcessTypeOrderByIdDesc(String processType);

    boolean existsByRecordNo(String recordNo);
}
