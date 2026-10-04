package com.glass.works.repository;

import com.glass.works.domain.TemperingBatch;
import com.glass.works.dto.FurnaceStatDTO;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TemperingBatchRepository extends JpaRepository<TemperingBatch, Long> {

    /**
     * 行级悲观锁读取钢化批次：推进 / 出炉登记串行化，
     * 防止同一批次并发重复推进、重复出炉（出炉结果只允许首次确认一次）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from TemperingBatch b where b.id = :id")
    Optional<TemperingBatch> findLockedById(@Param("id") Long id);

    /**
     * 手写 JPQL ⑤：按炉号统计出炉、破碎与平均加热温度（钢化良率看板）。
     */
    @Query("select new com.glass.works.dto.FurnaceStatDTO("
            + "b.furnaceNo, count(b), sum(b.outputCount), sum(b.brokenCount), avg(b.heatTemp)) "
            + "from TemperingBatch b "
            + "group by b.furnaceNo "
            + "order by sum(b.brokenCount) desc")
    List<FurnaceStatDTO> statByFurnace();

    List<TemperingBatch> findByFurnaceNoOrderByIdDesc(String furnaceNo);

    boolean existsByBatchNo(String batchNo);
}
