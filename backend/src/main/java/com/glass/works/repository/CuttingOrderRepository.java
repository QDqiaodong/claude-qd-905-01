package com.glass.works.repository;

import com.glass.works.domain.CuttingOrder;
import com.glass.works.dto.RemnantRankDTO;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CuttingOrderRepository extends JpaRepository<CuttingOrder, Long> {

    /**
     * 手写 JPQL ②：原片余料排行 —— 余料面积最大的切割单优先，便于二次套裁。
     */
    @Query("select new com.glass.works.dto.RemnantRankDTO("
            + "c.orderNo, c.batchNo, c.glassType, c.remnantAreaSqm, c.utilizationRate) "
            + "from CuttingOrder c "
            + "where c.remnantAreaSqm is not null and c.remnantAreaSqm > 0 "
            + "order by c.remnantAreaSqm desc")
    List<RemnantRankDTO> remnantRank();

    /**
     * 手写 JPQL ③：按利用率升序排出需要重新优化的切割单（低利用率 Top N）。
     */
    @Query("select c from CuttingOrder c "
            + "where c.status <> 'DONE' "
            + "order by c.outputAreaSqm / c.sourceAreaSqm asc")
    List<CuttingOrder> worstUtilization();

    List<CuttingOrder> findByStockIdOrderByIdDesc(Long stockId);

    boolean existsByOrderNo(String orderNo);
}
