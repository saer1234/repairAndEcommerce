package com.repairmanagement.repairproject.finance;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface InvoiceRepository extends JpaRepository<Invoice,Long> {
    List<Invoice> findByCompany_IdOrderByCreatedAtDesc(Long companyId);
    List<Invoice> findByCompany_IdAndCustomer_IdAndStatusNotOrderByCreatedAtDesc(Long companyId,Long customerId,InvoiceStatus status);
    Optional<Invoice> findByIdAndCompany_Id(Long id,Long companyId);
    java.util.Optional<Invoice> findByCompany_IdAndOrder_Id(Long companyId,Long orderId);
    boolean existsByOrder_Id(Long orderId);
    boolean existsByRepair_Id(Long repairId);
    Optional<Invoice> findByCompany_IdAndRepair_Id(Long companyId, Long repairId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.company.id=:companyId and i.id=:invoiceId")
    Optional<Invoice> lockForUpdate(@Param("companyId") Long companyId,@Param("invoiceId") Long invoiceId);
}
