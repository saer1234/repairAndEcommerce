package com.repairmanagement.repairproject.order;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    List<CustomerOrder> findByCompany_IdAndCustomer_IdOrderByOrderedAtDesc(Long companyId, Long customerId);
    List<CustomerOrder> findByCompany_IdOrderByOrderedAtDesc(Long companyId);
    List<CustomerOrder> findByCompany_IdAndStatus(Long companyId, OrderStatus status);
    Optional<CustomerOrder> findByIdAndCompany_Id(Long id, Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOrder o where o.id = :orderId and o.company.id = :companyId")
    Optional<CustomerOrder> lockForUpdate(@Param("companyId") Long companyId,
                                         @Param("orderId") Long orderId);
}
