package com.am9.ticket_ingestion_service.exception;

public class TicketNotFoundException extends RuntimeException{
    public TicketNotFoundException(String ticketId) {
        super("No ticket found with id: " + ticketId);
    }
}
