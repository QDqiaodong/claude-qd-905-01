package com.glass.works.repository;

import com.glass.works.domain.InspectionRecord;
import com.glass.works.dto.DefectTypeStatDTO;
import com.glass.works.dto.ProcessVerdictDTO;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InspectionRecordRepository extends JpaRepository<InspectionRecord, Long> {

    /**
     * 手写 JPQL ⑦（核心）：按工序环节统计合格 / 返修 / 报废数量。
     */
    @Query("select new com.glass.works.dto.ProcessVerdictDTO("
            + "i.processType, count(i), "
            + "sum(case when i.verdict = 'PASS' then 1 else 0 end), "
            + "sum(case when i.verdict = 'REWORK' then 1 else 0 end), "
            + "sum(case when i.verdict = 'SCRAP' then 1 else 0 end)) "
            + "from InspectionRecord i "
            + "group by i.processType")
    List<ProcessVerdictDTO> statVerdictByProcess();

    /**
     * 手写 JPQL ⑧：按缺陷类型统计次数、平均缺陷尺寸与涉及片数。
     */
    @Query("select new com.glass.works.dto.DefectTypeStatDTO("
            + "i.defectType, count(i), avg(i.defectSizeMm), sum(i.sheetCount)) "
            + "from InspectionRecord i "
            + "group by i.defectType "
            + "order by count(i) desc")
    List<DefectTypeStatDTO> statByDefectType();

    @Query("select i from InspectionRecord i "
            + "where (:processType is null or i.processType = :processType) "
            + "order by i.id desc")
    List<InspectionRecord> filter(@Param("processType") String processType);

    long countByVerdict(String verdict);

    boolean existsByInspectNo(String inspectNo);

    /** 返修批次详情：归并进该批次的全部检验记录（可追溯来源） */
    List<InspectionRecord> findByReworkIdOrderByIdAsc(Long reworkId);

    /** 历史归并：全部判返修的检验记录 */
    List<InspectionRecord> findByVerdictOrderByIdAsc(String verdict);
}
