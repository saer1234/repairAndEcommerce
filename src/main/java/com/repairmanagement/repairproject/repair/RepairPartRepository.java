package com.repairmanagement.repairproject.repair;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairPartRepository extends JpaRepository<RepairPart, Long> {
    List<RepairPart> findByRepair_IdOrderByIdAsc(Long repairId);
    boolean existsByRepair_Id(Long repairId);
    List<RepairPart> findByRepair_IdIn(List<Long> repairIds);
}
