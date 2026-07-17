package com.am9.ticket_ingestion_service.exception;

public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException() {
        super("Idempotency-Key was already used with a different request body");
    }
}