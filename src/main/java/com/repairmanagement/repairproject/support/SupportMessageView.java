package com.repairmanagement.repairproject.support;

import java.time.LocalDateTime;

public record SupportMessageView(Long id, Long conversationId, Long senderId,
                                 String senderName, String senderType, String body,
                                 LocalDateTime createdAt) {
    public static SupportMessageView of(SupportMessage m, SupportConversation thread) {
        return new SupportMessageView(m.getId(), thread.getId(), m.getSender().getId(),
            m.getSender().getFullName(),
            thread.getCustomer().getId().equals(m.getSender().getId()) ? "CUSTOMER" : "SUPPORT",
            m.getBody(), m.getCreatedAt());
    }
}
