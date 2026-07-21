package com.am9.ticket_ingestion_service.service.idempotency;

public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
