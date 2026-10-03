package com.glass.works.repository;

import com.glass.works.domain.TemperingBatch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TemperingBatchRepository extends JpaRepository<TemperingBatch, Long> {

    /**
     * 手写 JPQL ⑤：按炉号统计出炉、破碎与平均加热温度（钢化良率看板）。
     */
    @Query("select new com.glass.works.dto.FurnaceStatDTO("
            + "b.furnaceNo, count(b), sum(b.outputCount), sum(b.brokenCount), avg(b.heatTemp)) "
            + "from TemperingBatch b "
            + "group by b.furnaceNo "
            + "order by sum(b.brokenCount) desc")
    List<com.glass.works.dto.FurnaceStatDTO> statByFurnace();

    List<TemperingBatch> findByFurnaceNoOrderByIdDesc(String furnaceNo);

    boolean existsByBatchNo(String batchNo);
}
