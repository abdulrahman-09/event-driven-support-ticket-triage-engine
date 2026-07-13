package com.am9.ticket_triage_service.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {

    @Id
    private String id;

    private String subject;
    private String description;
    private String customerEmail;

    @Indexed
    private TicketStatus status;

    private String urgencyReasoning;
    private String category;
    private List<StatusChange> statusHistory = new ArrayList<>();

    @Indexed
    private Instant createdAt;

    private Instant updatedAt;

    public static Ticket newFromEvent(String id, String subject, String description,
                                      String customerEmail, Instant createdAt) {
        Ticket ticket = new Ticket();
        ticket.id = id;
        ticket.subject = subject;
        ticket.description = description;
        ticket.customerEmail = customerEmail;
        ticket.status = TicketStatus.STARTED;
        ticket.createdAt = createdAt;
        ticket.updatedAt = createdAt;
        ticket.statusHistory.add(new StatusChange(TicketStatus.STARTED, createdAt, "Ticket received"));
        return ticket;
    }

    public void appendStatusChange(TicketStatus newStatus, String note, Instant when) {
        this.status = newStatus;
        this.updatedAt = when;
        this.statusHistory.add(new StatusChange(newStatus, when, note));
    }
}
