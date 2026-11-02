package com.repairmanagement.repairproject.whish;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.company.CompanyRepository;
import com.repairmanagement.repairproject.finance.*;
import com.repairmanagement.repairproject.inventory.InventoryAccess;
import com.repairmanagement.repairproject.order.*;
import com.repairmanagement.repairproject.shopping.ShoppingEligibility;
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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WhishTransferServiceTest {
  InventoryAccess access=mock(InventoryAccess.class);
  UserService users=mock(UserService.class);
  ShoppingEligibility shoppers=mock(ShoppingEligibility.class);
  CompanyRepository companies=mock(CompanyRepository.class);
  CustomerOrderRepository orders=mock(CustomerOrderRepository.class);
  InvoiceRepository invoices=mock(InvoiceRepository.class);
  PaymentRepository payments=mock(PaymentRepository.class);
  WhishTransferRepository transfers=mock(WhishTransferRepository.class);
  WhishTransferService svc;
  Company company;
  User client,other,manager;
  CustomerOrder order;

  @BeforeEach void setup(){
    svc=new WhishTransferService(access,users,shoppers,companies,orders,invoices,payments,transfers);
    company=new Company("Repair Shop",null,null,null,"USD");
    ReflectionTestUtils.setField(company,"id",1L);
    company.setWhishWalletPhone("+96170123456");
    client=new User("Client","client@demo.test","hash",null);
    other=new User("Other","other@demo.test","hash",null);
    manager=new User("Manager","manager@demo.test","hash",null);
    ReflectionTestUtils.setField(client,"id",5L);
    ReflectionTestUtils.setField(other,"id",6L);
    ReflectionTestUtils.setField(manager,"id",7L);
    order=new CustomerOrder(company,client,"ORD-TEST",new BigDecimal("100.00"));
    ReflectionTestUtils.setField(order,"id",10L);
    when(orders.lockForUpdate(1L,10L)).thenReturn(Optional.of(order));
    when(access.requireActiveCompany(1L)).thenReturn(company);
    when(shoppers.requireClient("client@demo.test")).thenReturn(client);
    when(shoppers.requireClient("other@demo.test")).thenReturn(other);
    when(access.requireManager(1L,"manager@demo.test")).thenReturn(manager);
  }

  @Test void onlyOriginalClientMaySubmitReference(){
    var error=assertThrows(ResponseStatusException.class,()->svc.submitOrder(1L,10L,
      new WhishReferenceRequest("TEST-123456","Sender Name","+96170123456"),"other@demo.test"));
    assertEquals(HttpStatus.NOT_FOUND,error.getStatusCode());
    verify(transfers,never()).saveAndFlush(any());
  }

  @Test void sameOrderCannotHaveTwoPendingClaims(){
    WhishTransfer existing=new WhishTransfer(company,client,order,null,"FIRST-REF",new BigDecimal("100.00"),"Sender Name","+96170123456");
    when(transfers.findByOrder_IdOrderBySubmittedAtDesc(10L)).thenReturn(List.of(existing));
    var error=assertThrows(ResponseStatusException.class,()->svc.submitOrder(1L,10L,
      new WhishReferenceRequest("SECOND-REF","Sender Name","+96170123456"),"client@demo.test"));
    assertEquals(HttpStatus.CONFLICT,error.getStatusCode());
    verify(transfers,never()).saveAndFlush(any());
  }

  @Test void orderVerificationBeforeInvoiceDoesNotCreatePayment(){
    WhishTransfer claim=new WhishTransfer(company,client,order,null,"TEST-REF",new BigDecimal("100.00"),"Sender Name","+96170123456");
    when(transfers.lockTransfer(1L,21L)).thenReturn(Optional.of(claim));
    WhishView reviewed=svc.verify(1L,21L,"manager@demo.test");
    assertEquals(WhishStatus.VERIFIED,reviewed.status());
    assertFalse(reviewed.recordedOnInvoice());
    verify(payments,never()).saveAndFlush(any());
    assertThrows(ResponseStatusException.class,()->svc.verify(1L,21L,"manager@demo.test"));
  }

  @Test void verifiedOrderTransferIsReconciledExactlyOnceAfterInvoiceIssuance(){
    WhishTransfer claim=new WhishTransfer(company,client,order,null,"PAID-ON-WALLET",new BigDecimal("100.00"),"Sender Name","+96170123456");
    claim.verify(manager);
    Invoice invoice=new Invoice(company,client,order,null,"INV-TEST",
        new BigDecimal("100.00"),BigDecimal.ZERO);
    ReflectionTestUtils.setField(invoice,"id",50L);
    invoice.issue(java.time.LocalDateTime.now().plusDays(3));
    when(transfers.findByOrder_IdOrderBySubmittedAtDesc(10L)).thenReturn(List.of(claim));
    Payment paid=new Payment(invoice,new BigDecimal("100.00"),PaymentMethod.WHISH_TRANSFER,"PAID-ON-WALLET");
    when(payments.findByInvoice_IdOrderByIdAsc(50L))
        .thenReturn(List.of(),List.of(),List.of(paid));
    when(payments.saveAndFlush(any(Payment.class))).thenAnswer(inv->inv.getArgument(0));
    svc.reconcileVerifiedOrderTransfer(invoice);
    assertEquals(InvoiceStatus.PAID,invoice.getStatus());
    assertNotNull(claim.getReconciledPayment());
    svc.reconcileVerifiedOrderTransfer(invoice);
    verify(payments,times(1)).saveAndFlush(any(Payment.class));
  }


  @Test void transferKeepsSenderContactSeparateFromAuthenticatedClient(){
    when(transfers.saveAndFlush(any(WhishTransfer.class))).thenAnswer(inv->inv.getArgument(0));
    var result=svc.submitOrder(1L,10L,
        new WhishReferenceRequest("TRANSFER-900","Wallet Account Holder","+96176543210"),"client@demo.test");
    assertEquals(5L,result.customerId());
    assertEquals("Client",result.clientAccountName());
    assertEquals("Wallet Account Holder",result.senderName());
    assertEquals("+96176543210",result.senderPhone());
  }

  @Test void explicitRejectionAllowsCustomerRetry(){
    WhishTransfer claim=new WhishTransfer(company,client,order,null,"INVALID-REF",new BigDecimal("100.00"),"Sender Name","+96170123456");
    when(transfers.lockTransfer(1L,21L)).thenReturn(Optional.of(claim));
    WhishView rejected=svc.reject(1L,21L,new WhishReviewRequest("Nothing received"),"manager@demo.test");
    assertEquals(WhishStatus.REJECTED,rejected.status());
    assertEquals("Nothing received",rejected.reviewNote());
  }
}
