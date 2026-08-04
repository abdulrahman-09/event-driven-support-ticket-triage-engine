package com.am9.ticket_portal_service.entity;

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

    private TicketStatus status;

    private String urgencyReasoning;
    private String category;
    private List<StatusChange> statusHistory = new ArrayList<>();

    private Instant createdAt;

    private Instant updatedAt;
}
