package com.repairmanagement.repairproject.finance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRepository extends JpaRepository<Payment,Long>{
    List<Payment> findByInvoice_IdOrderByIdAsc(Long invoiceId);
    boolean existsByInvoice_IdAndProviderReference(Long invoiceId,String ref);
}
