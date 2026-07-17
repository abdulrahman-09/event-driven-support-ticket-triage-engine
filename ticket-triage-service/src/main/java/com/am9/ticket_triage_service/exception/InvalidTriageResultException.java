package com.am9.ticket_triage_service.exception;

public class InvalidTriageResultException extends RuntimeException {
    public InvalidTriageResultException(String message){
        super(message);
    }
}
