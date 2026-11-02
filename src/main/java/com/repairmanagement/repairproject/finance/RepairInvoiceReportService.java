package com.repairmanagement.repairproject.finance;

import com.repairmanagement.repairproject.inventory.InventoryAccess;
import com.repairmanagement.repairproject.repair.RepairPart;
import com.repairmanagement.repairproject.repair.RepairPartRepository;
import com.repairmanagement.repairproject.user.User;
import com.repairmanagement.repairproject.user.UserService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RepairInvoiceReportService {
    private final UserService users;
    private final InventoryAccess access;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository lines;
    private final RepairPartRepository repairParts;

    public RepairInvoiceReportService(UserService users, InventoryAccess access,
             InvoiceRepository invoices, InvoiceLineRepository lines, RepairPartRepository repairParts) {
        this.users=users;this.access=access;this.invoices=invoices;
        this.lines=lines;this.repairParts=repairParts;
    }

    @Transactional(readOnly=true)
    public RepairInvoiceReport customerReport(Long companyId,Long repairId,String email) {
        Invoice invoice=invoice(companyId,repairId);
        User user=users.currentUser(email);
        boolean manager=isManager(companyId,email);
        if (!manager && (!invoice.getCustomer().getId().equals(user.getId()) ||
            (invoice.getStatus()!=InvoiceStatus.ISSUED && invoice.getStatus()!=InvoiceStatus.PAID))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Repair invoice not available");
        }
        return safeReport(invoice);
    }

    @Transactional(readOnly=true)
    public RepairInvoiceInternalReport internalReport(Long companyId,Long repairId,String email) {
        access.requireManager(companyId,email);
        Invoice invoice=invoice(companyId,repairId);
        List<RepairInvoiceInternalLine> breakdown=new ArrayList<>();
        BigDecimal parts=BigDecimal.ZERO;
        for(RepairPart part: repairParts.findByRepair_IdOrderByIdAsc(repairId)) {
            BigDecimal total=part.getUnitCostPrice().multiply(BigDecimal.valueOf(part.getQuantity()));
            breakdown.add(new RepairInvoiceInternalLine(part.getItem().getName(), part.getQuantity(),
                part.getUnitCostPrice(),total));
            parts=parts.add(total);
        }
        // Cost values are snapshotted at invoice creation for reproducible reporting.
        BigDecimal snapParts=invoice.getRepairPartsCostSnapshot()==null?parts:invoice.getRepairPartsCostSnapshot();
        BigDecimal snapLabor=invoice.getRepairLaborCostSnapshot()==null?BigDecimal.ZERO:invoice.getRepairLaborCostSnapshot();
        breakdown.add(new RepairInvoiceInternalLine("Technician labor",1,snapLabor,snapLabor));
        BigDecimal cost=snapParts.add(snapLabor);
        return new RepairInvoiceInternalReport(safeReport(invoice),snapLabor,snapParts,cost,
             invoice.getSubtotal().subtract(cost),breakdown);
    }

    private Invoice invoice(Long companyId,Long repairId) {
        access.requireActiveCompany(companyId);
        return invoices.findByCompany_IdAndRepair_Id(companyId,repairId)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Repair invoice not found"));
    }
    private boolean isManager(Long companyId,String email) {
        try {access.requireManager(companyId,email);return true;}
        catch(ResponseStatusException ex) {return false;}
    }
    private RepairInvoiceReport safeReport(Invoice i) {
        var repair=i.getRepair();
        return new RepairInvoiceReport(i.getId(),i.getInvoiceNumber(),i.getStatus(),repair.getId(),
            i.getRepairDeviceSnapshot(),repair.getIssueDescription(),i.getRepairDiagnosisSnapshot(),
            i.getRepairWorkSnapshot(),i.getRepairTechnicianSnapshot(),
            repair.getClientName(),repair.getClientPhone(),
            i.getCompany().getName(),i.getCompany().getAddress(),i.getCompany().getEmail(),
            i.getCompany().getPhone(),i.getCompany().getCurrencyCode(),i.getIssuedAt(),i.getDueAt(),
            i.getSubtotal(),i.getTaxAmount(),i.getTotalAmount(),
            lines.findByInvoice_IdOrderByIdAsc(i.getId()).stream().map(InvoiceLineView::from).toList());
    }
}
