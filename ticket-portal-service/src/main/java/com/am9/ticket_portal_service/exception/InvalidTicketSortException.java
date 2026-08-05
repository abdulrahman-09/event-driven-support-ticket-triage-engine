package com.am9.ticket_portal_service.exception;

public class InvalidTicketSortException extends RuntimeException{
    public InvalidTicketSortException(String message){
        super(message);
    }
}
