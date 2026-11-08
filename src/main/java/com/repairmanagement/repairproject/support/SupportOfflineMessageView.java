package com.repairmanagement.repairproject.support;

import java.time.LocalDateTime;

public record SupportOfflineMessageView(Long id, Long companyId, Long customerId,
    String customerName, String customerEmail, String body, LocalDateTime createdAt,
    String reply, String repliedByName, LocalDateTime repliedAt) {
    public static SupportOfflineMessageView of(SupportOfflineMessage m) {
        return new SupportOfflineMessageView(m.getId(), m.getCompany().getId(),
            m.getCustomer().getId(), m.getCustomer().getFullName(), m.getCustomer().getEmail(),
            m.getBody(), m.getCreatedAt(), m.getReply(),
            m.getRepliedBy() == null ? null : m.getRepliedBy().getFullName(), m.getRepliedAt());
    }
}
