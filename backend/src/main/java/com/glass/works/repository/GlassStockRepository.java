package com.glass.works.repository;

import com.glass.works.domain.GlassStock;
import com.glass.works.dto.GlassTypeStatDTO;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GlassStockRepository extends JpaRepository<GlassStock, Long> {

    /**
     * 手写 JPQL ①：按玻璃种类统计在库批次、片数与总面积。
     * 面积 = 长 × 宽 × 片数 / 1,000,000，由数据库聚合后一次性返回。
     */
    @Query("select new com.glass.works.dto.GlassTypeStatDTO("
            + "s.glassType, count(s), sum(s.sheetCount), "
            + "sum(s.lengthMm * s.widthMm * s.sheetCount) / 1000000.0) "
            + "from GlassStock s "
            + "group by s.glassType "
            + "order by sum(s.lengthMm * s.widthMm * s.sheetCount) desc")
    List<GlassTypeStatDTO> statByGlassType();

    @Query("select s from GlassStock s "
            + "where (:glassType is null or s.glassType = :glassType) "
            + "and (:thickness is null or s.thicknessMm = :thickness) "
            + "order by s.thicknessMm, s.batchNo")
    List<GlassStock> filter(@Param("glassType") String glassType, @Param("thickness") Integer thickness);

    List<GlassStock> findByLocationId(Long locationId);

    boolean existsByBatchNo(String batchNo);
}
