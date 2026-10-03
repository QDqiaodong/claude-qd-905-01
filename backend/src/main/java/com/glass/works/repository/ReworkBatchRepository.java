package com.glass.works.repository;

import com.glass.works.domain.ReworkBatch;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReworkBatchRepository extends JpaRepository<ReworkBatch, Long> {

    /**
     * 行级悲观锁读取（SELECT ... FOR UPDATE）：
     * 所有状态流转都必须走这里，保证两名检验员同时提交相反结论时，
     * 后到的事务会阻塞到先者提交，再基于最新状态做前置校验 —— 只生效一个最终状态。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ReworkBatch b where b.id = :id")
    Optional<ReworkBatch> findLockedById(@Param("id") Long id);

    List<ReworkBatch> findAllByOrderByUpdatedAtDescIdDesc();

    List<ReworkBatch> findByStatusOrderByUpdatedAtDescIdDesc(String status);

    /** 未闭环（非已放行）批次：成品架上架 / 发运拦截、新返修判定归并都读这份共享状态 */
    List<ReworkBatch> findByStatusNot(String status);

    long countByStatus(String status);

    boolean existsByReworkNo(String reworkNo);
}
