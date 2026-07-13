package com.am9.ticket_triage_service.dto;

public record TriageResult(
        String urgency,
        String category,
        String reasoning
) {
}
