package com.am9.ticket_ingestion_service.dto;

import java.time.Instant;

public record TicketResponse(
        String ticketId,
        String status,
        String subject,
        String category,
        String urgencyReasoning,
        Instant createdAt
) {}
