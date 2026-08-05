package com.am9.ticket_portal_service.dto;

import com.am9.ticket_portal_service.entity.StatusChange;
import com.am9.ticket_portal_service.entity.TicketStatus;

import java.time.Instant;

public record StatusChangeResponse(
        TicketStatus status,
        Instant timestamp,
        String note
) {
    public static StatusChangeResponse from(StatusChange statusChange) {
        return new StatusChangeResponse(
                statusChange.status(),
                statusChange.timestamp(),
                statusChange.note()
        );
    }
}
