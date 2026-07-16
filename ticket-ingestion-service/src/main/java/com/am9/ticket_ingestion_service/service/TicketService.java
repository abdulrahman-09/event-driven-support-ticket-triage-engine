package com.am9.ticket_ingestion_service.service;

import com.am9.ticket_ingestion_service.dto.CreateTicketRequest;
import com.am9.ticket_ingestion_service.dto.TicketEvent;
import com.am9.ticket_ingestion_service.dto.TicketResponse;
import com.am9.ticket_ingestion_service.exception.DuplicateInFlightException;
import com.am9.ticket_ingestion_service.exception.TicketPublishFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketProducerService producerService;
    private final IdempotencyService idempotencyService;

    public TicketResponse createTicket(String idempotencyKey, CreateTicketRequest request){
        boolean claimed = idempotencyService.claim(idempotencyKey);

        if (!claimed){
            throw new DuplicateInFlightException(idempotencyKey);
        }

        String ticketId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        TicketEvent event = new TicketEvent(
                ticketId, request.subject(), request.description(), request.userEmail(), null,
                null, null, now, now
        );


        TicketResponse response = new TicketResponse(
                ticketId, request.subject(), request.description(), request.userEmail(), now
        );

        try {
            producerService.publishTicketCreated(event).get();
            return response;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            idempotencyService.release(idempotencyKey);
            throw new TicketPublishFailedException(ticketId, ex);
        } catch (ExecutionException ex) {
            idempotencyService.release(idempotencyKey);
            throw new TicketPublishFailedException(ticketId, ex);
        }
    }
}
