package com.repairmanagement.repairproject.finance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine,Long>{
    List<InvoiceLine> findByInvoice_IdOrderByIdAsc(Long invoiceId);
}
