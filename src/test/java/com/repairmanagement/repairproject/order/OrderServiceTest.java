package com.repairmanagement.repairproject.order;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.inventory.*;
import com.repairmanagement.repairproject.shopping.*;
import com.repairmanagement.repairproject.user.*;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    private ShoppingCartRepository carts;
    private CartItemRepository cartItems;
    private CustomerOrderRepository orders;
    private OrderItemRepository orderItems;
    private ItemRepository items;
    private StockMovementRepository stockMovements;
    private UserService users;
    private ShoppingEligibility shoppers;
    private InventoryAccess access;
    private com.repairmanagement.repairproject.whish.WhishTransferRepository whishTransfers;
    private OrderService service;
    private Company company;
    private User customer;
    private ShoppingCart cart;
    private Item item;

    @BeforeEach
    void setup() {
        carts = mock(ShoppingCartRepository.class);
        cartItems = mock(CartItemRepository.class);
        orders = mock(CustomerOrderRepository.class);
        orderItems = mock(OrderItemRepository.class);
        items = mock(ItemRepository.class);
        stockMovements = mock(StockMovementRepository.class);
        users = mock(UserService.class);
        shoppers = mock(ShoppingEligibility.class);
        access = mock(InventoryAccess.class);
        whishTransfers = mock(com.repairmanagement.repairproject.whish.WhishTransferRepository.class);
        service = new OrderService(carts, cartItems, orders, orderItems, items,
            stockMovements, users, shoppers, access, whishTransfers);
        company = new Company("Repair Store", null, null, null, "EUR");
        ReflectionTestUtils.setField(company, "id", 1L);
        customer = new User("Jane", "jane@example.com", "hash", null);
        ReflectionTestUtils.setField(customer, "id", 42L);
        cart = new ShoppingCart(company, customer);
        ReflectionTestUtils.setField(cart, "id", 15L);
        item = new Item(company, "SC-1", "LCD", null,
            new BigDecimal("50.00"), new BigDecimal("80.00"), 5);
        ReflectionTestUtils.setField(item, "id", 10L);
        when(users.currentUser("jane@example.com")).thenReturn(customer);
        when(shoppers.requireClient("jane@example.com")).thenReturn(customer);
        when(access.requireActiveCompany(1L)).thenReturn(company);
    }

    private CheckoutRequest delivery() {
        return new CheckoutRequest("+420123456789", "Main Street 10", "Kladno", "27201", "Czechia",
            "Ring the bell", new BigDecimal("50.1470000"), new BigDecimal("14.1050000"));
    }

    @Test
    void checkoutUsesLockedInventoryAndSavesHistoricalPrices() {
        CartItem line = new CartItem(cart, item, 2);
        when(carts.lockForUpdate(1L, 42L)).thenReturn(Optional.of(cart));
        when(cartItems.findByCart_IdOrderByIdAsc(15L)).thenReturn(List.of(line));
        when(items.lockForUpdate(1L, 10L)).thenReturn(Optional.of(item));
        when(orders.saveAndFlush(any(CustomerOrder.class))).thenAnswer(inv -> {
            CustomerOrder order = inv.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 99L);
            ReflectionTestUtils.setField(order, "orderedAt", LocalDateTime.now());
            return order;
        });

        OrderView result = service.checkout(1L, delivery(), "jane@example.com");
        assertEquals(OrderStatus.CONFIRMED, result.status());
        assertEquals(new BigDecimal("160.00"), result.totalAmount());
        assertEquals(3, item.getStockQuantity());
        assertEquals("Main Street 10", result.delivery().streetAddress());
        assertEquals(new BigDecimal("50.1470000"), result.delivery().latitude());
        verify(stockMovements).save(argThat(m -> m.getMovementType() == MovementType.SALE
            && m.getQuantityChange() == -2));
        verify(cartItems).deleteAll(List.of(line));
        verify(orderItems).saveAll(any());
        // Later catalog price edits must not modify saved transaction prices.
        OrderItem snapshot = new OrderItem(new CustomerOrder(company, customer, "ORD-1", result.totalAmount()), item, 2);
        item.updateDetails("SC-1", "LCD", null, new BigDecimal("60.00"), new BigDecimal("100.00"));
        assertEquals(new BigDecimal("50.00"), snapshot.getUnitCostPrice());
        assertEquals(new BigDecimal("80.00"), snapshot.getUnitSellingPrice());
    }

    @Test
    void deliveryFeeIsSavedAndIncludedInTheFinalOrderPrice() {
        company.configureDelivery(true,new BigDecimal("5.50"));
        CartItem line = new CartItem(cart,item,1);
        when(carts.lockForUpdate(1L,42L)).thenReturn(Optional.of(cart));
        when(cartItems.findByCart_IdOrderByIdAsc(15L)).thenReturn(List.of(line));
        when(items.lockForUpdate(1L,10L)).thenReturn(Optional.of(item));
        when(orders.saveAndFlush(any(CustomerOrder.class))).thenAnswer(call -> {
            CustomerOrder order=call.getArgument(0);
            ReflectionTestUtils.setField(order,"id",99L);
            ReflectionTestUtils.setField(order,"orderedAt",LocalDateTime.now());
            return order;
        });
        OrderView view=service.checkout(1L,delivery(),"jane@example.com");
        assertEquals(new BigDecimal("85.50"),view.totalAmount());
        assertEquals(new BigDecimal("80.00"),view.itemsSubtotal());
        assertEquals(new BigDecimal("5.50"),view.deliveryFee());
        assertEquals(FulfillmentMethod.DELIVERY,view.fulfillmentMethod());
    }

    @Test
    void pickupHasNoDeliveryFeeAndDoesNotNeedCoordinates() {
        company.configureDelivery(false,new BigDecimal("10.00"));
        CartItem line = new CartItem(cart,item,1);
        when(carts.lockForUpdate(1L,42L)).thenReturn(Optional.of(cart));
        when(cartItems.findByCart_IdOrderByIdAsc(15L)).thenReturn(List.of(line));
        when(items.lockForUpdate(1L,10L)).thenReturn(Optional.of(item));
        when(orders.saveAndFlush(any(CustomerOrder.class))).thenAnswer(call -> {
            CustomerOrder order=call.getArgument(0);
            ReflectionTestUtils.setField(order,"id",100L);
            ReflectionTestUtils.setField(order,"orderedAt",LocalDateTime.now());
            return order;
        });
        var pickup=new CheckoutRequest(null,null,null,null,null,null,null,null,new BigDecimal("80.00"),FulfillmentMethod.PICKUP);
        OrderView view=service.checkout(1L,pickup,"jane@example.com");
        assertEquals(new BigDecimal("80.00"),view.totalAmount());
        assertEquals(new BigDecimal("0"),view.deliveryFee());
        assertEquals(FulfillmentMethod.PICKUP,view.fulfillmentMethod());
    }

    @Test
    void checkoutRejectsDeliveryWhenDisabled() {
        company.configureDelivery(false,new BigDecimal("3.00"));
        var line = new CartItem(cart,item,1);
        when(carts.lockForUpdate(1L,42L)).thenReturn(Optional.of(cart));
        when(cartItems.findByCart_IdOrderByIdAsc(15L)).thenReturn(List.of(line));
        when(items.lockForUpdate(1L,10L)).thenReturn(Optional.of(item));
        assertThrows(ResponseStatusException.class,()->service.checkout(1L,delivery(),"jane@example.com"));
        verify(orders,never()).saveAndFlush(any());
    }

    @Test
    void checkoutRejectsInsufficientStockWithoutSavingOrderOrMovement() {
        CartItem line = new CartItem(cart, item, 6);
        when(carts.lockForUpdate(1L, 42L)).thenReturn(Optional.of(cart));
        when(cartItems.findByCart_IdOrderByIdAsc(15L)).thenReturn(List.of(line));
        when(items.lockForUpdate(1L, 10L)).thenReturn(Optional.of(item));
        ResponseStatusException err = assertThrows(ResponseStatusException.class,
            () -> service.checkout(1L, delivery(), "jane@example.com"));
        assertEquals(HttpStatus.CONFLICT, err.getStatusCode());
        assertEquals(5, item.getStockQuantity());
        verify(orders, never()).saveAndFlush(any());
        verify(stockMovements, never()).save(any());
    }

    @Test
    void profitAccessRequiresManagerAndCostSnapshotsAreInternal() {
        CustomerOrder order = new CustomerOrder(company, customer, "ORD-10", new BigDecimal("80.00"));
        ReflectionTestUtils.setField(order, "id", 10L);
        when(orders.findByIdAndCompany_Id(10L, 1L)).thenReturn(Optional.of(order));
        when(orderItems.findByOrder_IdOrderByIdAsc(10L)).thenReturn(List.of(new OrderItem(order,item,1)));
        OrderProfitView summary = service.orderProfit(1L, 10L, "jane@example.com");
        assertEquals(new BigDecimal("30.00"), summary.grossProfit());
        verify(access).requireManager(1L, "jane@example.com");
    }

    @Test
    void cancelledOrderRestocksExactlyOnce() {
        CustomerOrder order = new CustomerOrder(company, customer, "ORD-123", new BigDecimal("80.00"));
        ReflectionTestUtils.setField(order, "id", 88L);
        ReflectionTestUtils.setField(order, "orderedAt", LocalDateTime.now());
        OrderItem line = new OrderItem(order, item, 1);
        when(orders.lockForUpdate(1L, 88L)).thenReturn(Optional.of(order));
        when(orderItems.findByOrder_IdOrderByIdAsc(88L)).thenReturn(List.of(line));
        when(items.lockForUpdate(1L, 10L)).thenReturn(Optional.of(item));

        OrderView result = service.cancel(1L, 88L, "jane@example.com");
        assertEquals(OrderStatus.CANCELLED, result.status());
        assertEquals(6, item.getStockQuantity());
        verify(stockMovements).save(argThat(m -> m.getMovementType() == MovementType.RETURN
            && m.getQuantityChange() == 1));

        assertThrows(ResponseStatusException.class, () -> service.cancel(1L, 88L, "jane@example.com"));
        assertEquals(6, item.getStockQuantity());
    }

    @Test
    void completedSalesSummaryUsesSnapshotsOnly() {
        CustomerOrder complete = new CustomerOrder(company, customer, "ORD-2", new BigDecimal("160.00"));
        complete.complete();
        OrderItem line = new OrderItem(complete, item, 2);
        when(orders.findByCompany_IdAndStatus(1L, OrderStatus.COMPLETED)).thenReturn(List.of(complete));
        when(orderItems.findByOrder_Company_IdAndOrder_Status(1L, OrderStatus.COMPLETED))
            .thenReturn(List.of(line));

        SalesSummaryView result = service.salesSummary(1L, "jane@example.com");
        assertEquals(1, result.completedOrders());
        assertEquals(new BigDecimal("160.00"), result.completedSalesRevenue());
        assertEquals(new BigDecimal("100.00"), result.costOfGoodsSold());
        assertEquals(new BigDecimal("60.00"), result.completedGrossProfit());
    }

    @Test
    void differentCompanyOrderCannotBeRead() {
        when(orders.findByIdAndCompany_Id(21L, 77L)).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.get(21L, 77L, "jane@example.com"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }
    @Test
    void nonClientCheckoutIsDeniedBeforeOrderAndStockMutations() {
        when(shoppers.requireClient("jane@example.com"))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Client only"));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.checkout(1L, delivery(), "jane@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verifyNoInteractions(carts, cartItems, orders, orderItems, stockMovements);
    }
}
