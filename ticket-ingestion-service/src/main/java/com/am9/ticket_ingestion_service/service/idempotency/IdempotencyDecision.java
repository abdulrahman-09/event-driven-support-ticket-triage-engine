package com.am9.ticket_ingestion_service.service.idempotency;

import com.am9.ticket_ingestion_service.dto.TicketResponse;

public record IdempotencyDecision(
        boolean shouldProcess,
        TicketResponse cachedResponse
) {
    public static IdempotencyDecision processNewRequest() {
        return new IdempotencyDecision(true, null);
    }

    public static IdempotencyDecision returnCachedResponse(TicketResponse response) {
        return new IdempotencyDecision(false, response);
    }
}
