package com.am9.ticket_ingestion_service.exception;

import lombok.Getter;

@Getter
public class DuplicateInFlightException extends RuntimeException {
    private final String idempotencyKey;
    public DuplicateInFlightException(String idempotencyKey) {
        super("Request already in progress for this idempotency key");
        this.idempotencyKey = idempotencyKey;
    }
}
