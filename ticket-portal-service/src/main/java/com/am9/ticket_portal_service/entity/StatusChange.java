package com.am9.ticket_portal_service.entity;

import java.time.Instant;

public record StatusChange(
        TicketStatus status,
        Instant timestamp,
        String note
) {}