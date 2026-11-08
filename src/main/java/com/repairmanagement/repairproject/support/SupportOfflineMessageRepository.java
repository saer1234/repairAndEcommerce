package com.repairmanagement.repairproject.support;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportOfflineMessageRepository extends JpaRepository<SupportOfflineMessage, Long> {
    @EntityGraph(attributePaths = {"company", "customer", "repliedBy"})
    List<SupportOfflineMessage> findTop100ByCompany_IdAndCustomer_IdOrderByCreatedAtDesc(Long companyId, Long customerId);
    @EntityGraph(attributePaths = {"company", "customer", "repliedBy"})
    List<SupportOfflineMessage> findTop100ByCompany_IdOrderByCreatedAtDesc(Long companyId);
    @EntityGraph(attributePaths = {"company", "customer", "repliedBy"})
    Optional<SupportOfflineMessage> findByIdAndCompany_Id(Long id, Long companyId);
}
