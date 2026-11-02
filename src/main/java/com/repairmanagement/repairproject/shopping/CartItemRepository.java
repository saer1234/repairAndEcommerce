package com.repairmanagement.repairproject.shopping;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCart_IdOrderByIdAsc(Long cartId);
    Optional<CartItem> findByCart_IdAndItem_Id(Long cartId, Long itemId);
}
