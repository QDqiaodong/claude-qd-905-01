package com.glass.works.repository;

import com.glass.works.domain.CuttingPiece;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuttingPieceRepository extends JpaRepository<CuttingPiece, Long> {

    List<CuttingPiece> findByCuttingOrderIdOrderBySortNoAscIdAsc(Long cuttingOrderId);

    void deleteByCuttingOrderId(Long cuttingOrderId);
}
