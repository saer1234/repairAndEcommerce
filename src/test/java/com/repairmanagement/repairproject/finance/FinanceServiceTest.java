package com.repairmanagement.repairproject.finance;

import com.repairmanagement.repairproject.company.*;
import com.repairmanagement.repairproject.inventory.InventoryAccess;
import com.repairmanagement.repairproject.order.*;
import com.repairmanagement.repairproject.repair.*;
import com.repairmanagement.repairproject.user.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FinanceServiceTest {
    FinanceService finance;
    InventoryAccess access = mock(InventoryAccess.class);
    UserService users = mock(UserService.class);
    CompanyRepository companies = mock(CompanyRepository.class);
    CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
    OrderItemRepository orderItems = mock(OrderItemRepository.class);
    RepairRequestRepository repairs = mock(RepairRequestRepository.class);
    RepairPartRepository parts = mock(RepairPartRepository.class);
    InvoiceRepository invoices = mock(InvoiceRepository.class);
    InvoiceLineRepository lines = mock(InvoiceLineRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    ExpenseRepository expenses = mock(ExpenseRepository.class);
    PlatformFeeRepository fees = mock(PlatformFeeRepository.class);
    OrderService orderService = mock(OrderService.class);
    RepairService repairService = mock(RepairService.class);
    com.repairmanagement.repairproject.whish.WhishTransferService whishService = mock(com.repairmanagement.repairproject.whish.WhishTransferService.class);
    Company company;
    User manager, customer;

    @BeforeEach void setup() {
        finance = new FinanceService(access, users, companies, orders, orderItems, repairs,
            parts, invoices, lines, payments, expenses, fees, orderService, repairService, whishService);
        company = new Company("Repair Co", null, null, null, "EUR");
        ReflectionTestUtils.setField(company, "id", 1L);
        manager = new User("Manager", "manager@demo.test", "hash", null);
        customer = new User("Customer", "customer@demo.test", "hash", null);
        ReflectionTestUtils.setField(manager, "id", 2L);
        ReflectionTestUtils.setField(customer, "id", 3L);
        when(access.requireManager(1L, "manager@demo.test")).thenReturn(manager);
        when(access.requireActiveCompany(1L)).thenReturn(company);
        when(users.currentUser("customer@demo.test")).thenReturn(customer);
        when(users.currentUser("manager@demo.test")).thenReturn(manager);
    }

    @Test void mustSupplyOneInvoiceSource() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> finance.createInvoice(1L, new CreateInvoiceRequest(null, null, BigDecimal.ZERO),
                "manager@demo.test"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(invoices, never()).saveAndFlush(any());
    }

    @Test void cannotInvoiceSameOrderTwice() {
        CustomerOrder order = new CustomerOrder(company, customer, "ORD-1", new BigDecimal("80.00"));
        ReflectionTestUtils.setField(order, "id", 20L);
        order.complete();
        when(orders.lockForUpdate(1L,20L)).thenReturn(Optional.of(order));
        when(invoices.existsByOrder_Id(20L)).thenReturn(true);
        ResponseStatusException ex=assertThrows(ResponseStatusException.class,
            ()->finance.createInvoice(1L,new CreateInvoiceRequest(20L,null,BigDecimal.ZERO),"manager@demo.test"));
        assertEquals(HttpStatus.CONFLICT,ex.getStatusCode());
        verify(invoices,never()).saveAndFlush(any());
    }

    @Test void cannotInvoiceUnfinishedRepair() {
        RepairRequest repair=new RepairRequest(company,customer,"Phone",null,null,"Screen broken");
        ReflectionTestUtils.setField(repair,"id",30L);
        when(repairs.lockForUpdate(1L,30L)).thenReturn(Optional.of(repair));
        ResponseStatusException ex=assertThrows(ResponseStatusException.class,
            ()->finance.createInvoice(1L,new CreateInvoiceRequest(null,30L,BigDecimal.ZERO),"manager@demo.test"));
        assertEquals(HttpStatus.CONFLICT,ex.getStatusCode());
    }

    @Test void issuedInvoiceTriggersReconciliationOfEarlierVerifiedWhishTransfer() {
        Invoice i=invoice();
        when(invoices.lockForUpdate(1L,40L)).thenReturn(Optional.of(i));
        finance.issueInvoice(1L,40L,
            new IssueInvoiceRequest(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(3)),
            "manager@demo.test");
        verify(whishService).reconcileVerifiedOrderTransfer(i);
    }

    @Test void preventsOverpayment() {
        Invoice i=invoice();i.issue(java.time.LocalDateTime.now().plusDays(3));
        Payment previous=new Payment(i,new BigDecimal("75.00"),PaymentMethod.CASH,"CASH-0001");
        when(invoices.lockForUpdate(1L,40L)).thenReturn(Optional.of(i));
        when(payments.findByInvoice_IdOrderByIdAsc(40L)).thenReturn(List.of(previous));
        ResponseStatusException ex=assertThrows(ResponseStatusException.class,
            ()->finance.recordReceivedPayment(1L,40L,
                new RecordPaymentRequest(new BigDecimal("30.00"),PaymentMethod.CASH,"CASH-0002"),"manager@demo.test"));
        assertEquals(HttpStatus.CONFLICT,ex.getStatusCode());
        verify(payments,never()).saveAndFlush(any());
    }

    @Test void preventsDuplicatePaymentReference() {
        Invoice i=invoice();i.issue(java.time.LocalDateTime.now().plusDays(3));
        when(invoices.lockForUpdate(1L,40L)).thenReturn(Optional.of(i));
        when(payments.existsByInvoice_IdAndProviderReference(40L,"BANK-REF")).thenReturn(true);
        ResponseStatusException ex=assertThrows(ResponseStatusException.class,
            ()->finance.recordReceivedPayment(1L,40L,
                new RecordPaymentRequest(new BigDecimal("10.00"),PaymentMethod.BANK_TRANSFER,"BANK-REF"),"manager@demo.test"));
        assertEquals(HttpStatus.CONFLICT,ex.getStatusCode());
    }

    @Test void blocksUnverifiedOnlinePayments() {
        Invoice i=invoice();i.issue(java.time.LocalDateTime.now().plusDays(3));
        when(invoices.lockForUpdate(1L,40L)).thenReturn(Optional.of(i));
        ResponseStatusException ex=assertThrows(ResponseStatusException.class,
            ()->finance.recordReceivedPayment(1L,40L,
                new RecordPaymentRequest(new BigDecimal("10.00"),PaymentMethod.ONLINE,"fake"),"manager@demo.test"));
        assertEquals(HttpStatus.BAD_REQUEST,ex.getStatusCode());
        verify(payments,never()).saveAndFlush(any());
    }

    @Test void prohibitsCustomerReadingAnotherCustomersInvoice() {
        Invoice i=invoice();i.issue(java.time.LocalDateTime.now().plusDays(3));
        when(invoices.findByIdAndCompany_Id(40L,1L)).thenReturn(Optional.of(i));
        User stranger=new User("Stranger","stranger@demo.test","hash",null);
        ReflectionTestUtils.setField(stranger,"id",99L);
        when(users.currentUser("stranger@demo.test")).thenReturn(stranger);
        when(access.requireManager(1L,"stranger@demo.test"))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        ResponseStatusException ex=assertThrows(ResponseStatusException.class,
            ()->finance.getInvoice(1L,40L,"stranger@demo.test"));
        assertEquals(HttpStatus.NOT_FOUND,ex.getStatusCode());
    }

    @Test void companySummarySeparatesCashFromGrossProfit() {
        when(orderService.salesSummary(1L,"manager@demo.test"))
            .thenReturn(new SalesSummaryView(1L,"EUR",1,new BigDecimal("160.00"),
                new BigDecimal("100.00"),new BigDecimal("60.00")));
        when(repairService.profitSummary(1L,"manager@demo.test"))
            .thenReturn(new RepairProfitSummaryView(1L,"EUR",1,new BigDecimal("100.00"),
                new BigDecimal("80.00"),new BigDecimal("35.00"),new BigDecimal("50.00"),new BigDecimal("95.00")));
        when(expenses.findByCompany_IdOrderByExpenseDateDescIdDesc(1L)).thenReturn(List.of(
            new Expense(company,"RENT",null,new BigDecimal("20.00"),"EUR",
                java.time.LocalDate.now(),manager)));
        when(fees.findByCompany_Id(1L)).thenReturn(List.of());
        when(invoices.findByCompany_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        CompanyFinanceView summary=finance.companyOverview(1L,"manager@demo.test");
        assertEquals(0,new BigDecimal("155.00").compareTo(summary.grossProfit()));
        assertEquals(0,new BigDecimal("135.00").compareTo(summary.estimatedOperatingResultBeforeTax()));
        assertEquals(0,BigDecimal.ZERO.compareTo(summary.cashCollectedIncludingTax()));
    }


    @Test void createsInvoiceUsingHistoricalOrderPriceSnapshot() {
        CustomerOrder order = new CustomerOrder(company, customer, "ORD-10", new BigDecimal("160.00"));
        ReflectionTestUtils.setField(order, "id", 20L);
        order.complete();
        when(orders.lockForUpdate(1L, 20L)).thenReturn(Optional.of(order));
        com.repairmanagement.repairproject.inventory.Item item =
            new com.repairmanagement.repairproject.inventory.Item(company,"SKU-1","Screen",null,
                new BigDecimal("50.00"),new BigDecimal("80.00"),10);
        OrderItem line = new OrderItem(order,item,2);
        when(orderItems.findByOrder_IdOrderByIdAsc(20L)).thenReturn(List.of(line));
        when(invoices.saveAndFlush(any(Invoice.class))).thenAnswer(inv->{
            Invoice saved=inv.getArgument(0);
            ReflectionTestUtils.setField(saved,"id",40L);
            return saved;
        });
        InvoiceView v = finance.createInvoice(1L,
            new CreateInvoiceRequest(20L,null,new BigDecimal("8.00")),"manager@demo.test");
        assertEquals(InvoiceStatus.DRAFT,v.status());
        assertEquals(0,new BigDecimal("160.00").compareTo(v.subtotal()));
        assertEquals(0,new BigDecimal("168.00").compareTo(v.totalAmount()));
        verify(lines).saveAll(argThat(all->{
            InvoiceLine first=all.iterator().next();
            return first.getDescription().equals("Screen") &&
                first.getUnitPrice().compareTo(new BigDecimal("80.00"))==0;
        }));
    }

    @Test void fullReceivedPaymentMarksInvoicePaid() {
        Invoice i=invoice();i.issue(java.time.LocalDateTime.now().plusDays(3));
        when(invoices.lockForUpdate(1L,40L)).thenReturn(Optional.of(i));
        when(payments.findByInvoice_IdOrderByIdAsc(40L)).thenReturn(List.of());
        finance.recordReceivedPayment(1L,40L,
            new RecordPaymentRequest(new BigDecimal("100.00"),PaymentMethod.CASH,"CASH-100"),
            "manager@demo.test");
        assertEquals(InvoiceStatus.PAID,i.getStatus());
        verify(payments).saveAndFlush(any(Payment.class));
    }

    @Test void platformOverviewKeepsCurrenciesSeparate() {
        User platformAdmin = new User("Administrator","super@demo.test","hash",null);
        ReflectionTestUtils.setField(platformAdmin,"platformRole",PlatformRole.SUPER_ADMIN);
        when(users.currentUser("super@demo.test")).thenReturn(platformAdmin);
        PlatformFee eurFee = new PlatformFee(company,null,null,FeeType.SUBSCRIPTION,new BigDecimal("50.00"));
        eurFee.earn();
        Company czCompany = new Company("Czech Shop",null,null,null,"CZK");
        PlatformFee czFee = new PlatformFee(czCompany,null,null,FeeType.SUBSCRIPTION,new BigDecimal("100.00"));
        czFee.earn();
        when(fees.findAll()).thenReturn(List.of(eurFee,czFee));
        when(expenses.findByCompanyIsNullOrderByExpenseDateDescIdDesc()).thenReturn(List.of(
            new Expense(null,"HOSTING",null,new BigDecimal("10.00"),"EUR",
                java.time.LocalDate.now(),platformAdmin)));
        List<PlatformCurrencySummary> results=finance.platformOverview("super@demo.test");
        assertEquals(2,results.size());
        assertEquals("CZK",results.get(0).currencyCode());
        assertEquals(0,new BigDecimal("40.00").compareTo(results.get(1).estimatedOperatingResultBeforeTax()));
    }
    private Invoice invoice() {
        CustomerOrder order=new CustomerOrder(company,customer,"ORD-42",new BigDecimal("100.00"));
        Invoice invoice=new Invoice(company,customer,order,null,"INV-100",new BigDecimal("100.00"),BigDecimal.ZERO);
        ReflectionTestUtils.setField(invoice,"id",40L);
        return invoice;
    }
}
