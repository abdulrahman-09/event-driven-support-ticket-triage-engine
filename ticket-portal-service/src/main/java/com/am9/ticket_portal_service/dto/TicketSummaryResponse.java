package com.am9.ticket_portal_service.dto;

import com.am9.ticket_portal_service.entity.Ticket;
import com.am9.ticket_portal_service.entity.TicketStatus;

import java.time.Instant;

public record TicketSummaryResponse(
        long index,
        String id,
        String subject,
        String customerEmail,
        TicketStatus status,
        String urgencyReasoning,
        String category,
        Instant createdAt,
        Instant updatedAt
) {
    public static TicketSummaryResponse from(Ticket ticket, long index) {
        return new TicketSummaryResponse(
                index,
                ticket.getId(),
                ticket.getSubject(),
                ticket.getCustomerEmail(),
                ticket.getStatus(),
                ticket.getUrgencyReasoning(),
                ticket.getCategory(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
