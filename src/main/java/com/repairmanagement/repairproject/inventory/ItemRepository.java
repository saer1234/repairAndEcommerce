package com.repairmanagement.repairproject.inventory;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findByCompany_IdAndActiveTrueOrderByNameAsc(Long companyId);
    List<Item> findByCompany_IdAndActiveTrueAndVisibleToClientsTrueOrderByNameAsc(Long companyId);
    List<Item> findByCompany_IdOrderByNameAsc(Long companyId);
    Optional<Item> findByIdAndCompany_Id(Long itemId, Long companyId);
    Optional<Item> findByIdAndCompany_IdAndActiveTrue(Long itemId, Long companyId);
    Optional<Item> findByIdAndCompany_IdAndActiveTrueAndVisibleToClientsTrue(Long itemId, Long companyId);
    boolean existsByCompany_IdAndSkuIgnoreCase(Long companyId, String sku);
    boolean existsByCompany_IdAndSkuIgnoreCaseAndIdNot(Long companyId, String sku, Long itemId);

    // Pessimistic database lock ensures two simultaneous stock updates cannot overwrite each other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.company.id = :companyId and i.id = :itemId")
    Optional<Item> lockForUpdate(@Param("companyId") Long companyId, @Param("itemId") Long itemId);
}
