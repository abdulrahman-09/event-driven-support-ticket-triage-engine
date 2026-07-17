package com.am9.ticket_triage_service.dto;

import com.am9.ticket_triage_service.model.TicketStatus;

public record ValidatedTriageResult(
        TicketStatus urgency,
        String category,
        String reasoning
) {
}
