package com.am9.ticket_ingestion_service.exception;

public class InvalidIdempotencyKeyException extends RuntimeException {
    public InvalidIdempotencyKeyException(String message) {
        super(message);
    }
}
