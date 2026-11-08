package com.repairmanagement.repairproject.support;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportConversationRepository extends JpaRepository<SupportConversation, Long> {
    @EntityGraph(attributePaths = {"company", "customer"})
    Optional<SupportConversation> findByCompany_IdAndCustomer_Id(Long companyId, Long customerId);
    @EntityGraph(attributePaths = {"company", "customer"})
    Optional<SupportConversation> findByIdAndCompany_Id(Long id, Long companyId);
    @EntityGraph(attributePaths = {"company", "customer"})
    List<SupportConversation> findByCompany_IdOrderByUpdatedAtDesc(Long companyId);
}
