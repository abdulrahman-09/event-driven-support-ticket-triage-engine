package com.am9.ticket_triage_service.exception;

public class InvalidTicketEventException extends RuntimeException {
    public InvalidTicketEventException(String message) {
        super(message);
    }
}
