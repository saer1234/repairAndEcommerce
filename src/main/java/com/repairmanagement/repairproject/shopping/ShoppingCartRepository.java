package com.repairmanagement.repairproject.shopping;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShoppingCartRepository extends JpaRepository<ShoppingCart, Long> {
    Optional<ShoppingCart> findByCompany_IdAndCustomer_Id(Long companyId, Long customerId);

    // Serialize cart changes/checkout for this customer + company.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ShoppingCart c where c.company.id = :companyId and c.customer.id = :customerId")
    Optional<ShoppingCart> lockForUpdate(@Param("companyId") Long companyId,
                                          @Param("customerId") Long customerId);
}
