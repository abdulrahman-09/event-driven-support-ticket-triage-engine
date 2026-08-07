package com.am9.ticket_portal_service.exception;

public class TicketNotFoundException extends RuntimeException{
    public TicketNotFoundException(String ticketId){
        super("No support ticket with id: " + ticketId);
    }
}
