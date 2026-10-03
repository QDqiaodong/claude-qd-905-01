package com.glass.works.repository;

import com.glass.works.domain.EdgeGrinding;
import com.glass.works.dto.EdgeStatDTO;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EdgeGrindingRepository extends JpaRepository<EdgeGrinding, Long> {

    /**
     * 手写 JPQL ④：按边型统计加工量与崩边量（磨边工序质量看板）。
     */
    @Query("select new com.glass.works.dto.EdgeStatDTO("
            + "g.edgeType, count(g), sum(g.pieceCount), sum(g.chipCount), "
            + "sum(case when g.chipCount > 0 then 1 else 0 end)) "
            + "from EdgeGrinding g "
            + "group by g.edgeType")
    List<EdgeStatDTO> statByEdgeType();

    List<EdgeGrinding> findByCuttingOrderIdOrderByIdDesc(Long cuttingOrderId);
}
