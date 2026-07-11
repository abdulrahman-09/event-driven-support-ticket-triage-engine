package com.am9.ticket_ingestion_service.dto;

import java.time.Instant;

public record TicketEvent(
        String ticketId,
        String subject,
        String description,
        String userEmail,
        String urgency,      // null on tickets.created; CRITICAL/MEDIUM/LOW on routing topics; null on tickets.dlq
        String category,     // null on tickets.created and tickets.dlq
        String reasoning,    // null on tickets.created; failure summary on tickets.dlq
        Instant createdAt,
        Instant occurredAt
) {}