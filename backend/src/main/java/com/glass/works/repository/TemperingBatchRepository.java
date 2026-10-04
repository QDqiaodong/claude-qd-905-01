package com.glass.works.repository;

import com.glass.works.domain.TemperingBatch;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TemperingBatchRepository extends JpaRepository<TemperingBatch, Long> {

    /**
     * 行级悲观锁读取（SELECT ... FOR UPDATE）：
     * 炉内推进、出炉登记与历史补录都必须走这里，保证已出炉批次的最终结果
     * 在并发重复提交下也不会被改写 —— 后到事务阻塞到先者提交后再做前置校验。
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
    List<com.glass.works.dto.FurnaceStatDTO> statByFurnace();

    List<TemperingBatch> findByFurnaceNoOrderByIdDesc(String furnaceNo);

    boolean existsByBatchNo(String batchNo);
}
