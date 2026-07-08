package com.am9.ticket_ingestion_service.exception;

public class MissingIdempotencyKeyException extends RuntimeException{
    public MissingIdempotencyKeyException(){
        super("Idempotency-Key header is required");
    }
}
