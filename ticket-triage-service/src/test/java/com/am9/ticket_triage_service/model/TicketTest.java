package com.am9.ticket_triage_service.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TicketTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void newFromEvent_initializesAStartedUnroutedTicketAndItsHistory() {
        Ticket ticket = Ticket.newFromEvent("ticket-1", "Cannot sign in", "Reset link fails",
                "ana@example.com", CREATED_AT);

        assertThat(ticket.getId()).isEqualTo("ticket-1");
        assertThat(ticket.getSubject()).isEqualTo("Cannot sign in");
        assertThat(ticket.getDescription()).isEqualTo("Reset link fails");
        assertThat(ticket.getCustomerEmail()).isEqualTo("ana@example.com");
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.STARTED);
        assertThat(ticket.isRoutePublished()).isFalse();
        assertThat(ticket.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(ticket.getUpdatedAt()).isEqualTo(CREATED_AT);
        assertThat(ticket.getStatusHistory()).containsExactly(
                new StatusChange(TicketStatus.STARTED, CREATED_AT, "Ticket received"));
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"CRITICAL", "MEDIUM", "LOW", "FAILED"})
    void appendStatusChange_updatesCurrentStateAndRetainsExistingHistory(TicketStatus status) {
        Ticket ticket = Ticket.newFromEvent("ticket-1", "Subject", "Description", "ana@example.com", CREATED_AT);
        Instant changedAt = CREATED_AT.plusSeconds(60);

        ticket.appendStatusChange(status, "State changed", changedAt);

        assertThat(ticket.getStatus()).isEqualTo(status);
        assertThat(ticket.getUpdatedAt()).isEqualTo(changedAt);
        assertThat(ticket.getStatusHistory()).containsExactly(
                new StatusChange(TicketStatus.STARTED, CREATED_AT, "Ticket received"),
                new StatusChange(status, changedAt, "State changed"));
    }
}
