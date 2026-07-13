package com.am9.ticket_triage_service.model;

import java.time.Instant;

public record StatusChange(
        TicketStatus status,
        Instant timestamp,
        String note
) {}