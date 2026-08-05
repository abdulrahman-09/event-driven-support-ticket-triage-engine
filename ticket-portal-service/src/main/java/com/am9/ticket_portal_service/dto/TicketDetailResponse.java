package com.am9.ticket_portal_service.dto;

import com.am9.ticket_portal_service.entity.Ticket;
import com.am9.ticket_portal_service.entity.TicketStatus;

import java.time.Instant;
import java.util.List;

public record TicketDetailResponse(
        String id,
        String subject,
        String description,
        String customerEmail,
        TicketStatus status,
        String urgencyReasoning,
        String category,
        List<StatusChangeResponse> statusHistory,
        Instant createdAt,
        Instant updatedAt
) {
    public static TicketDetailResponse from(Ticket ticket) {
        List<StatusChangeResponse> history = ticket.getStatusHistory() == null
                ? List.of()
                : ticket.getStatusHistory().stream()
                .map(StatusChangeResponse::from)
                .toList();

        return new TicketDetailResponse(
                ticket.getId(),
                ticket.getSubject(),
                ticket.getDescription(),
                ticket.getCustomerEmail(),
                ticket.getStatus(),
                ticket.getUrgencyReasoning(),
                ticket.getCategory(),
                history,
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
