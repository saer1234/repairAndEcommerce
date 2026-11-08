package com.repairmanagement.repairproject.support;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    @EntityGraph(attributePaths = {"sender"})
    List<SupportMessage> findByConversation_IdOrderByIdDesc(Long conversationId, Pageable pageable);
}
