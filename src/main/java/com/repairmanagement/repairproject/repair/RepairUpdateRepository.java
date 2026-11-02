package com.repairmanagement.repairproject.repair;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairUpdateRepository extends JpaRepository<RepairUpdate, Long> {
    List<RepairUpdate> findByRepair_IdOrderByCreatedAtAscIdAsc(Long repairId);
}
