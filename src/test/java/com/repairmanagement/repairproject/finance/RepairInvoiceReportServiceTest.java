package com.repairmanagement.repairproject.finance;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.inventory.InventoryAccess;
import com.repairmanagement.repairproject.repair.RepairPartRepository;
import com.repairmanagement.repairproject.repair.RepairRequest;
import com.repairmanagement.repairproject.user.User;
import com.repairmanagement.repairproject.user.UserService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RepairInvoiceReportServiceTest {
    final UserService users=mock(UserService.class);
    final InventoryAccess access=mock(InventoryAccess.class);
    final InvoiceRepository invoices=mock(InvoiceRepository.class);
    final InvoiceLineRepository lines=mock(InvoiceLineRepository.class);
    final RepairPartRepository parts=mock(RepairPartRepository.class);
    RepairInvoiceReportService service;
    Company shop;
    User customer, manager;
    Invoice invoice;

    @BeforeEach void setup() {
        service=new RepairInvoiceReportService(users,access,invoices,lines,parts);
        shop=new Company("Example Repairs",null,null,null,"EUR");
        ReflectionTestUtils.setField(shop,"id",10L);
        customer=new User("Alex Customer","alex@example.test","hash","1234567");
        manager=new User("Repair Manager","manager@example.test","hash","9876543");
        ReflectionTestUtils.setField(customer,"id",2L);
        ReflectionTestUtils.setField(manager,"id",3L);
        RepairRequest repair=new RepairRequest(shop,customer,"Phone","Samsung","S24","Broken screen");
        ReflectionTestUtils.setField(repair,"id",7L);
        repair.assign(manager);
        repair.recordReport("Display glass shattered","Replaced display and tested touchscreen");
        repair.setLabor(new BigDecimal("40.00"),new BigDecimal("15.00"));
        invoice=new Invoice(shop,customer,null,repair,"INV-R007",new BigDecimal("100.00"),new BigDecimal("10.00"));
        ReflectionTestUtils.setField(invoice,"id",23L);
        invoice.snapshotPartCost(new BigDecimal("30.00"));
        when(invoices.findByCompany_IdAndRepair_Id(10L,7L)).thenReturn(Optional.of(invoice));
        when(access.requireActiveCompany(10L)).thenReturn(shop);
        when(lines.findByInvoice_IdOrderByIdAsc(23L)).thenReturn(List.of());
        when(parts.findByRepair_IdOrderByIdAsc(7L)).thenReturn(List.of());
        when(users.currentUser("alex@example.test")).thenReturn(customer);
        when(users.currentUser("manager@example.test")).thenReturn(manager);
        when(access.requireManager(10L,"manager@example.test")).thenReturn(manager);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
            .when(access).requireManager(10L,"alex@example.test");
    }

    @Test void customerCannotSeeDraftInvoice() {
        ResponseStatusException ex=assertThrows(ResponseStatusException.class,
            ()->service.customerReport(10L,7L,"alex@example.test"));
        assertEquals(HttpStatus.NOT_FOUND,ex.getStatusCode());
    }

    @Test void issuedCustomerInvoiceHasDetailedWorkButNoInternalCostFields() {
        invoice.issue(LocalDateTime.now().plusDays(14));
        RepairInvoiceReport report=service.customerReport(10L,7L,"alex@example.test");
        assertEquals("Repair Manager",report.technicianName());
        assertEquals("Display glass shattered",report.diagnosis());
        assertEquals("Replaced display and tested touchscreen",report.workPerformed());
        assertEquals(new BigDecimal("110.00"),report.totalAmount());
        assertTrue(java.util.Arrays.stream(RepairInvoiceReport.class.getRecordComponents())
            .noneMatch(c->c.getName().toLowerCase().contains("cost") ||
                          c.getName().toLowerCase().contains("profit")));
    }

    @Test void managerOnlyCanSeeGrossProfit() {
        RepairInvoiceInternalReport report=service.internalReport(10L,7L,"manager@example.test");
        assertEquals(new BigDecimal("45.00"),report.totalDirectCost());
        assertEquals(new BigDecimal("55.00"),report.grossProfitBeforeTax());
        assertThrows(ResponseStatusException.class,
            ()->service.internalReport(10L,7L,"alex@example.test"));
    }
}
