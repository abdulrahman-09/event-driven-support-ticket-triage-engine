package com.am9.ticket_ingestion_service.service;

import com.am9.ticket_ingestion_service.dto.CreateTicketRequest;
import com.am9.ticket_ingestion_service.service.idempotency.IdempotencyDecision;
import com.am9.ticket_ingestion_service.messaging.TicketEvent;
import com.am9.ticket_ingestion_service.dto.TicketResponse;
import com.am9.ticket_ingestion_service.exception.TicketPublishFailedException;
import com.am9.ticket_ingestion_service.service.idempotency.IdempotencyService;
import com.am9.ticket_ingestion_service.service.messaging.TicketProducerService;
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
        String requestHash = idempotencyService.fingerprint(request);
        IdempotencyDecision decision = idempotencyService.startOrReturnCompleted(idempotencyKey, requestHash);

        if (!decision.shouldProcess()){
            return decision.cachedResponse();
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
            idempotencyService.complete(idempotencyKey, requestHash, response);
            return response;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            idempotencyService.fail(idempotencyKey, requestHash, ex.getMessage());
            throw new TicketPublishFailedException(ticketId, ex);
        } catch (ExecutionException ex) {
            idempotencyService.fail(idempotencyKey, requestHash, ex.getMessage());
            throw new TicketPublishFailedException(ticketId, ex);
        }
    }
}
