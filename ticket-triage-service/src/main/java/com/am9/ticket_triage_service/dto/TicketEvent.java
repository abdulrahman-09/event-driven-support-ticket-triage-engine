package com.am9.ticket_triage_service.dto;

import java.time.Instant;

public record TicketEvent(
        String ticketId,
        String subject,
        String description,
        String userEmail,
        String urgency,
        String category,
        String reasoning,
        Instant createdAt,
        Instant occurredAt
) {
}
