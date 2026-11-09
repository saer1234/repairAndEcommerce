package com.repairmanagement.repairproject.repair;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepairRequestRepository extends JpaRepository<RepairRequest, Long> {
    List<RepairRequest> findByCompany_IdOrderByCreatedAtDesc(Long companyId);
    List<RepairRequest> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);
    Optional<RepairRequest> findByIdAndCustomer_Id(Long id, Long customerId);
    List<RepairRequest> findByCompany_IdAndCustomer_IdOrderByCreatedAtDesc(Long companyId, Long customerId);
    List<RepairRequest> findByCompany_IdAndStatusIn(Long companyId, List<RepairStatus> statuses);
    List<RepairRequest> findByCompany_IdAndStatusInAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
        Long companyId, List<RepairStatus> statuses, java.time.LocalDateTime start, java.time.LocalDateTime end);
    Optional<RepairRequest> findByIdAndCompany_Id(Long id, Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RepairRequest r where r.company.id = :companyId and r.id = :repairId")
    Optional<RepairRequest> lockForUpdate(@Param("companyId") Long companyId, @Param("repairId") Long repairId);
}
