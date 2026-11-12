package com.repairmanagement.repairproject.support;

import com.repairmanagement.repairproject.company.CompanyMembershipChanged;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Revokes support credentials only after a successful membership transaction. */
@Component
public class SupportMembershipRevocation {
    private final SupportWebSocketTickets tickets;
    private final SupportWebSocketHub hub;
    private final SupportAgentPresenceRepository presence;

    public SupportMembershipRevocation(SupportWebSocketTickets tickets, SupportWebSocketHub hub,
                                       SupportAgentPresenceRepository presence) {
        this.tickets = tickets;
        this.hub = hub;
        this.presence = presence;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revoked(CompanyMembershipChanged event) {
        tickets.revoke(event.companyId(), event.userId());
        hub.revoke(event.companyId(), event.userId());
        presence.deleteForMember(event.companyId(), event.userId());
    }
}
