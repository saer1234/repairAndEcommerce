package com.repairmanagement.repairproject.finance;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.inventory.InventoryAccess;
import com.repairmanagement.repairproject.order.*;
import com.repairmanagement.repairproject.repair.*;
import com.repairmanagement.repairproject.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompanyRevenueRangeServiceTest {
  @Test void revenueIncludesDeliveryAndCompletedWorkButNotUncompletedOrders() {
    var access=mock(InventoryAccess.class);
    var orders=mock(CustomerOrderRepository.class);
    var items=mock(OrderItemRepository.class);
    var repairs=mock(RepairRequestRepository.class);
    var parts=mock(RepairPartRepository.class);
    var shop=new Company("Repair",null,null,null,"USD");
    var customer=new User("Client","a@example.com","hash",null);
    var order=new CustomerOrder(shop,customer,"ORD-A",new BigDecimal("100.00"));
    order.snapshotPrice(new BigDecimal("100.00"),new BigDecimal("10.00"));
    org.springframework.test.util.ReflectionTestUtils.setField(order,"id",5L);
    var item=mock(OrderItem.class);
    when(item.getUnitSellingPrice()).thenReturn(new BigDecimal("50.00"));
    when(item.getQuantity()).thenReturn(2);
    var repair=mock(RepairRequest.class);
    when(repair.getLaborCharge()).thenReturn(new BigDecimal("30.00"));
    when(repair.getId()).thenReturn(8L);
    when(orders.findByCompany_IdAndStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
        eq(1L),eq(OrderStatus.COMPLETED),any(),any())).thenReturn(List.of(order));
    when(items.findByOrder_IdOrderByIdAsc(any())).thenReturn(List.of(item));
    when(repairs.findByCompany_IdAndStatusInAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
        eq(1L),anyList(),any(),any())).thenReturn(List.of(repair));
    when(parts.findByRepair_IdOrderByIdAsc(any())).thenReturn(List.of());
    when(access.requireActiveCompany(1L)).thenReturn(shop);
    var service=new CompanyRevenueRangeService(access,orders,items,repairs,parts);
    var result=service.report(1L,LocalDate.now().minusDays(7),LocalDate.now(),"manager@example.com");
    assertEquals(new BigDecimal("100.00"),result.productRevenue());
    assertEquals(new BigDecimal("10.00"),result.deliveryFees());
    assertEquals(new BigDecimal("30.00"),result.repairRevenue());
    assertEquals(new BigDecimal("140.00"),result.totalRevenue());
    verify(access).requireManager(1L,"manager@example.com");
  }
}
