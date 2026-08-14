package com.am9.ticket_ingestion_service.service.idempotency;

import com.am9.ticket_ingestion_service.dto.TicketResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyRecordTest {

    private final Instant createdAt = Instant.parse("2026-01-02T03:04:05Z");
    private final Instant updatedAt = Instant.parse("2026-01-02T03:05:05Z");

    @Test
    void processing_initializesAnInFlightRecord() {
        IdempotencyRecord record = IdempotencyRecord.processing("request-hash", createdAt);

        assertThat(record.requestHash()).isEqualTo("request-hash");
        assertThat(record.status()).isEqualTo(IdempotencyStatus.PROCESSING);
        assertThat(record.response()).isNull();
        assertThat(record.errorMessage()).isNull();
        assertThat(record.createdAt()).isEqualTo(createdAt);
        assertThat(record.updatedAt()).isEqualTo(createdAt);
    }

    @Test
    void completed_preservesTheOriginalClaimAndStoresTheResponse() {
        IdempotencyRecord processing = IdempotencyRecord.processing("request-hash", createdAt);
        TicketResponse response = response();

        IdempotencyRecord completed = processing.completed(response, updatedAt);

        assertThat(completed.requestHash()).isEqualTo("request-hash");
        assertThat(completed.status()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(completed.response()).isSameAs(response);
        assertThat(completed.errorMessage()).isNull();
        assertThat(completed.createdAt()).isEqualTo(createdAt);
        assertThat(completed.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void failed_preservesTheOriginalClaimAndStoresTheFailure() {
        IdempotencyRecord processing = IdempotencyRecord.processing("request-hash", createdAt);

        IdempotencyRecord failed = processing.failed("Kafka unavailable", updatedAt);

        assertThat(failed.requestHash()).isEqualTo("request-hash");
        assertThat(failed.status()).isEqualTo(IdempotencyStatus.FAILED);
        assertThat(failed.response()).isNull();
        assertThat(failed.errorMessage()).isEqualTo("Kafka unavailable");
        assertThat(failed.createdAt()).isEqualTo(createdAt);
        assertThat(failed.updatedAt()).isEqualTo(updatedAt);
    }

    private TicketResponse response() {
        return new TicketResponse("ticket-1", "Subject", "Description", "ana@example.com", createdAt);
    }
}
