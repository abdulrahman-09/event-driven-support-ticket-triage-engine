package com.am9.ticket_ingestion_service.model;

import java.time.Instant;

public record StatusChange(
        TicketStatus status,
        Instant timestamp,
        String note
) {
}
