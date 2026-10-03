package com.glass.works.repository;

import com.glass.works.domain.FinishedRack;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FinishedRackRepository extends JpaRepository<FinishedRack, Long> {

    /**
     * 手写 JPQL ⑨：按发货状态统计成品架数、片数与总面积。
     */
    @Query("select new com.glass.works.dto.RackStatDTO("
            + "r.shipStatus, count(r), sum(r.sheetCount), sum(r.areaSqm)) "
            + "from FinishedRack r "
            + "group by r.shipStatus")
    List<com.glass.works.dto.RackStatDTO> statByShipStatus();

    List<FinishedRack> findByOrderNo(String orderNo);

    boolean existsByRackNo(String rackNo);
}
