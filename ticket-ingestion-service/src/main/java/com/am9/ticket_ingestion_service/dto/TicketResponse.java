package com.am9.ticket_ingestion_service.dto;

import java.time.Instant;

public record TicketResponse(
        String ticketId,
        String subject,
        String description,
        String userEmail,
        Instant createdAt
) {}
