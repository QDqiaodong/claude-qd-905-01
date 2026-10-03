package com.glass.works.repository;

import com.glass.works.domain.StorageLocation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageLocationRepository extends JpaRepository<StorageLocation, Long> {

    List<StorageLocation> findByZoneCodeOrderByRackCodeAscLevelNoDesc(String zoneCode);

    List<StorageLocation> findAllByOrderByZoneCodeAscRackCodeAscLevelNoDesc();
}
