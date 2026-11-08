package com.repairmanagement.repairproject.support;

import java.time.LocalDateTime;

public record SupportConversationView(
    Long id, Long companyId, String companyName, Long customerId,
    String customerName, String customerEmail, SupportStatus status,
    LocalDateTime updatedAt
) {
    public static SupportConversationView of(SupportConversation c) {
        return new SupportConversationView(c.getId(), c.getCompany().getId(), c.getCompany().getName(),
            c.getCustomer().getId(), c.getCustomer().getFullName(), c.getCustomer().getEmail(),
            c.getStatus(), c.getUpdatedAt());
    }
}
