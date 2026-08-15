package com.am9.ticket_ingestion_service.service.idempotency;

import com.am9.ticket_ingestion_service.dto.TicketResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyDecisionTest {

    @Test
    void processNewRequest_requiresProcessingAndHasNoCachedResponse() {
        IdempotencyDecision decision = IdempotencyDecision.processNewRequest();

        assertThat(decision.shouldProcess()).isTrue();
        assertThat(decision.cachedResponse()).isNull();
    }

    @Test
    void returnCachedResponse_retainsTheResponse() {
        TicketResponse cached = response();

        IdempotencyDecision decision = IdempotencyDecision.returnCachedResponse(cached);

        assertThat(decision.shouldProcess()).isFalse();
        assertThat(decision.cachedResponse()).isSameAs(cached);
    }

    private TicketResponse response() {
        return new TicketResponse("ticket-1", "Subject", "Description", "ana@example.com",
                Instant.parse("2026-01-02T03:04:05Z"));
    }
}