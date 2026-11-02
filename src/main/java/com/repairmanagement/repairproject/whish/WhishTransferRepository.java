package com.repairmanagement.repairproject.whish;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface WhishTransferRepository extends JpaRepository<WhishTransfer,Long> {
  List<WhishTransfer> findByCompany_IdOrderBySubmittedAtDesc(Long companyId);
  List<WhishTransfer> findByOrder_IdOrderBySubmittedAtDesc(Long orderId);
  List<WhishTransfer> findByInvoice_IdOrderBySubmittedAtDesc(Long invoiceId);
  boolean existsByCompany_IdAndTransferReference(Long companyId,String reference);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from WhishTransfer t where t.company.id=:companyId and t.id=:id")
  Optional<WhishTransfer> lockTransfer(@Param("companyId") Long companyId,@Param("id") Long id);
}
