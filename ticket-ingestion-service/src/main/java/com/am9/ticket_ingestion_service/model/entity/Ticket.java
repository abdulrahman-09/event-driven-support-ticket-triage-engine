package com.am9.ticket_ingestion_service.model.entity;

import com.am9.ticket_ingestion_service.model.StatusChange;
import com.am9.ticket_ingestion_service.model.enums.TicketStatus;
import lombok.Getter;
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

    public void appendStatusChange(TicketStatus newStatus, String note, Instant when) {
        this.status = newStatus;
        this.updatedAt = when;
        this.statusHistory.add(new StatusChange(newStatus, when, note));
    }

}
