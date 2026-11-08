package com.repairmanagement.repairproject.order;

import com.repairmanagement.repairproject.company.Company;
import com.repairmanagement.repairproject.inventory.*;
import com.repairmanagement.repairproject.shopping.*;
import com.repairmanagement.repairproject.user.User;
import com.repairmanagement.repairproject.user.UserService;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {
    private final ShoppingCartRepository carts;
    private final CartItemRepository cartItems;
    private final CustomerOrderRepository orders;
    private final OrderItemRepository orderItems;
    private final ItemRepository items;
    private final StockMovementRepository stockMovements;
    private final UserService users;
    private final ShoppingEligibility shoppers;
    private final InventoryAccess access;
    private final com.repairmanagement.repairproject.whish.WhishTransferRepository whishTransfers;

    public OrderService(ShoppingCartRepository carts, CartItemRepository cartItems,
                        CustomerOrderRepository orders, OrderItemRepository orderItems,
                        ItemRepository items, StockMovementRepository stockMovements,
                        UserService users, ShoppingEligibility shoppers, InventoryAccess access,
                        com.repairmanagement.repairproject.whish.WhishTransferRepository whishTransfers) {
        this.carts = carts;
        this.cartItems = cartItems;
        this.orders = orders;
        this.orderItems = orderItems;
        this.items = items;
        this.stockMovements = stockMovements;
        this.users = users;
        this.shoppers = shoppers;
        this.access = access;
        this.whishTransfers = whishTransfers;
    }

    @Transactional
    public OrderView checkout(Long companyId, CheckoutRequest delivery, String email) {
        User customer = shoppers.requireClient(email);
        Company company = access.requireActiveCompany(companyId);
        ShoppingCart cart = carts.lockForUpdate(companyId, customer.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Cart is empty"));
        List<CartItem> contents = cartItems.findByCart_IdOrderByIdAsc(cart.getId());
        if (contents.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart is empty");
        }
        // Lock item rows in a stable order; validate all before changing any stock.
        List<CartItem> sorted = contents.stream()
            .sorted(Comparator.comparing(c -> c.getItem().getId())).toList();
        List<Item> locked = new java.util.ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem cartLine : sorted) {
            Item item = items.lockForUpdate(companyId, cartLine.getItem().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Item no longer available"));
            if (!item.isActive() || !item.isVisibleToClients() || cartLine.getQuantity() <= 0 ||
                item.getStockQuantity() < cartLine.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "One or more cart items are unavailable or out of stock");
            }
            locked.add(item);
            total = total.add(item.getEffectiveSellingPrice().multiply(BigDecimal.valueOf(cartLine.getQuantity())));
        }
        // If the offer changed after the cart preview, ask the customer to review the new total.
        if (delivery.expectedTotal() != null && delivery.expectedTotal().compareTo(total) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Cart prices changed. Refresh the cart and confirm the latest total before ordering");
        }
        CustomerOrder order = new CustomerOrder(company, customer,
            "ORD-" + UUID.randomUUID().toString().toUpperCase(), total);
        order.setDelivery(delivery);
        order = orders.saveAndFlush(order);
        List<OrderItem> completedLines = new java.util.ArrayList<>();
        for (int idx = 0; idx < sorted.size(); idx++) {
            CartItem cartLine = sorted.get(idx);
            Item item = locked.get(idx);
            OrderItem line = new OrderItem(order, item, cartLine.getQuantity(), item.getEffectiveSellingPrice());
            completedLines.add(line);
            item.setStockQuantity(item.getStockQuantity() - cartLine.getQuantity());
            stockMovements.save(new StockMovement(item, customer, MovementType.SALE,
                -cartLine.getQuantity(), "Checkout " + order.getOrderNumber()));
        }
        orderItems.saveAll(completedLines);
        cartItems.deleteAll(contents);
        cartItems.flush();
        return OrderView.of(order, completedLines);
    }

    @Transactional(readOnly = true)
    public List<OrderView> mine(Long companyId, String email) {
        User customer = users.currentUser(email);
        access.requireActiveCompany(companyId);
        return orders.findByCompany_IdAndCustomer_IdOrderByOrderedAtDesc(companyId, customer.getId())
            .stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderView> all(Long companyId, String email) {
        access.requireManager(companyId, email);
        return orders.findByCompany_IdOrderByOrderedAtDesc(companyId)
            .stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public OrderView get(Long companyId, Long orderId, String email) {
        User viewer = users.currentUser(email);
        access.requireActiveCompany(companyId);
        CustomerOrder order = orders.findByIdAndCompany_Id(orderId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!viewer.getId().equals(order.getCustomer().getId())) {
            access.requireManager(companyId, email);
        }
        return toView(order);
    }

    // This is a manual fulfillment marker, not proof of payment.
    @Transactional
    public OrderView complete(Long companyId, Long orderId, String email) {
        access.requireManager(companyId, email);
        CustomerOrder order = lockOrder(companyId, orderId);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only confirmed orders can be completed");
        }
        order.complete();
        return toView(order);
    }

    @Transactional
    public OrderView cancel(Long companyId, Long orderId, String email) {
        User user = users.currentUser(email);
        access.requireActiveCompany(companyId);
        CustomerOrder order = lockOrder(companyId, orderId);
        if (!order.getCustomer().getId().equals(user.getId())) {
            access.requireManager(companyId, email);
        }
        if (whishTransfers.findByOrder_IdOrderBySubmittedAtDesc(orderId).stream()
            .anyMatch(t -> t.getStatus() != com.repairmanagement.repairproject.whish.WhishStatus.REJECTED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Order has a pending or verified Whish transfer; resolve/refund it before cancellation");
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only confirmed orders can be cancelled");
        }
        List<OrderItem> orderLines = orderItems.findByOrder_IdOrderByIdAsc(orderId);
        List<OrderItem> sorted = orderLines.stream()
            .sorted(Comparator.comparing(line -> line.getItem().getId())).toList();
        for (OrderItem line : sorted) {
            Item item = items.lockForUpdate(companyId, line.getItem().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Order item missing"));
            long quantity = (long) item.getStockQuantity() + line.getQuantity();
            if (quantity > Integer.MAX_VALUE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock quantity overflow");
            }
            item.setStockQuantity((int) quantity);
            stockMovements.save(new StockMovement(item, user, MovementType.RETURN,
                line.getQuantity(), "Cancelled " + order.getOrderNumber()));
        }
        order.cancel();
        return OrderView.of(order, orderLines);
    }

    @Transactional(readOnly = true)
    public SalesSummaryView salesSummary(Long companyId, String email) {
        access.requireManager(companyId, email);
        Company company = access.requireActiveCompany(companyId);
        List<CustomerOrder> completed = orders.findByCompany_IdAndStatus(companyId, OrderStatus.COMPLETED);
        List<OrderItem> lines = orderItems.findByOrder_Company_IdAndOrder_Status(companyId, OrderStatus.COMPLETED);
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (OrderItem line : lines) {
            BigDecimal count = BigDecimal.valueOf(line.getQuantity());
            revenue = revenue.add(line.getUnitSellingPrice().multiply(count));
            cost = cost.add(line.getUnitCostPrice().multiply(count));
        }
        return new SalesSummaryView(companyId, company.getCurrencyCode(), completed.size(),
            revenue, cost, revenue.subtract(cost));
    }

    @Transactional(readOnly = true)
    public OrderProfitView orderProfit(Long companyId, Long orderId, String email) {
        access.requireManager(companyId, email);
        CustomerOrder order = orders.findByIdAndCompany_Id(orderId, companyId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return OrderProfitView.of(order, orderItems.findByOrder_IdOrderByIdAsc(orderId));
    }

    private CustomerOrder lockOrder(Long companyId, Long orderId) {
        return orders.lockForUpdate(companyId, orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }
    private OrderView toView(CustomerOrder order) {
        return OrderView.of(order, orderItems.findByOrder_IdOrderByIdAsc(order.getId()));
    }
}
