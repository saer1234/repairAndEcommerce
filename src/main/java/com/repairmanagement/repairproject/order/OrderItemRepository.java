package com.repairmanagement.repairproject.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrder_IdOrderByIdAsc(Long orderId);
    List<OrderItem> findByOrder_Company_IdAndOrder_Status(Long companyId, OrderStatus status);
}
