package com.am9.ticket_ingestion_service.exception;

import lombok.Getter;

@Getter
public class TicketPublishFailedException extends RuntimeException {
    private final String ticketId;
    public TicketPublishFailedException(String ticketId, Throwable cause) {
        super("Failed to publish ticket created event for ticketId: " + ticketId, cause);
        this.ticketId = ticketId;
    }
}
