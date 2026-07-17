package com.am9.ticket_triage_service.ai;

import com.am9.ticket_triage_service.dto.TriageResult;
import com.am9.ticket_triage_service.dto.ValidatedTriageResult;
import com.am9.ticket_triage_service.exception.InvalidTriageResultException;
import com.am9.ticket_triage_service.model.TicketStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class TriageResultValidator {

    private static final int MAX_CATEGORY_LENGTH = 50;
    private static final int MAX_REASONING_LENGTH = 500;

    public ValidatedTriageResult validate(TriageResult result) {
        if (result == null) {
            throw new InvalidTriageResultException("AI returned an empty triage result");
        }

        TicketStatus urgency = parseUrgency(result.urgency());
        String category = normalizeRequiredText("category", result.category(), MAX_CATEGORY_LENGTH);
        String reasoning = normalizeRequiredText("reasoning", result.reasoning(), MAX_REASONING_LENGTH);

        return new ValidatedTriageResult(urgency, category, reasoning);
    }

    private TicketStatus parseUrgency(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidTriageResultException("AI returned missing urgency");
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "CRITICAL" -> TicketStatus.CRITICAL;
            case "MEDIUM" -> TicketStatus.MEDIUM;
            case "LOW" -> TicketStatus.LOW;
            default -> throw new InvalidTriageResultException(
                    "AI returned unsupported urgency: " + value
            );
        };
    }

    private String normalizeRequiredText(String fieldName, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidTriageResultException("AI returned missing " + fieldName);
        }

        String normalized = value.trim().replaceAll("\\s+", " ");

        if (normalized.length() > maxLength) {
            throw new InvalidTriageResultException(
                    "AI returned " + fieldName + " longer than " + maxLength + " characters"
            );
        }

        return normalized;
    }
}
