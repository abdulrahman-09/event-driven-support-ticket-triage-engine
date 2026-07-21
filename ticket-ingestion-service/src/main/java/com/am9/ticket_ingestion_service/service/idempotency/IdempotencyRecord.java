package com.am9.ticket_ingestion_service.service.idempotency;

import com.am9.ticket_ingestion_service.dto.TicketResponse;

import java.time.Instant;

public record IdempotencyRecord(
        String requestHash,
        IdempotencyStatus status,
        TicketResponse response,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static IdempotencyRecord processing(String requestHash, Instant now) {
        return new IdempotencyRecord(
                requestHash,
                IdempotencyStatus.PROCESSING,
                null,
                null,
                now,
                now
        );
    }

    public IdempotencyRecord completed(TicketResponse response, Instant now) {
        return new IdempotencyRecord(
                requestHash,
                IdempotencyStatus.COMPLETED,
                response,
                null,
                createdAt,
                now
        );
    }
    public IdempotencyRecord failed(String errorMessage, Instant now) {
        return new IdempotencyRecord(requestHash,
                IdempotencyStatus.FAILED,
                null,
                errorMessage,
                createdAt,
                now);
    }
}
