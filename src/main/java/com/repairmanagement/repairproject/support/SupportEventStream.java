package com.repairmanagement.repairproject.support;

import org.springframework.stereotype.Component;

/** Compatibility adapter: existing REST writers now notify WebSocket clients rather than SSE streams. */
@Component
public class SupportEventStream {
    private final SupportWebSocketHub sockets;

    public SupportEventStream(SupportWebSocketHub sockets) { this.sockets = sockets; }

    public void changed(Long companyId, Long customerId) {
        sockets.changed(companyId, customerId);
    }

    public void message(Long companyId, Long customerId, SupportMessageView message) {
        sockets.messageAdded(companyId, customerId, message);
    }
}
