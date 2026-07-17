package com.am9.ticket_triage_service.consumer;

import com.am9.ticket_triage_service.ai.TriageClassifier;
import com.am9.ticket_triage_service.ai.TriageResultValidator;
import com.am9.ticket_triage_service.dto.TicketEvent;
import com.am9.ticket_triage_service.dto.TriageResult;
import com.am9.ticket_triage_service.dto.ValidatedTriageResult;
import com.am9.ticket_triage_service.model.Ticket;
import com.am9.ticket_triage_service.model.TicketStatus;
import com.am9.ticket_triage_service.producer.TriageEventProducer;
import com.am9.ticket_triage_service.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketConsumer {

    private final TriageClassifier triageClassifier;
    private final TicketRepository ticketRepository;
    private final TriageEventProducer triageEventProducer;
    private final TriageResultValidator triageResultValidator;

    @KafkaListener(topics = "${app.kafka.topic.tickets-created}", concurrency = "3")
    public void handleTicketCreated(TicketEvent event){
        log.info("Received ticket {} for triage", event.ticketId());

        Ticket ticket = Ticket.newFromEvent(
                event.ticketId(), event.subject(), event.description(),
                event.userEmail(), event.createdAt()
        );

        try{

            ticketRepository.save(ticket);
            TriageResult rawResult = triageClassifier.classify(event);
            ValidatedTriageResult validatedResult = triageResultValidator.validate(rawResult);
            applyClassification(ticket, validatedResult, event);
        }catch (Exception ex){
            log.error("Failed to process ticket {}: {}", event.ticketId(), ex.getMessage());
            handleFailure(ticket, event, ex.getMessage());
        }
    }

    private void applyClassification(Ticket ticket, ValidatedTriageResult result, TicketEvent event) {
        Instant now = Instant.now();
        String note = "Classified as " + result.urgency() + ": " + result.reasoning();

        ticket.setCategory(result.category());
        ticket.setUrgencyReasoning(result.reasoning());
        ticket.appendStatusChange(result.urgency(), note, now);
        ticketRepository.save(ticket);

        TicketEvent classifiedEvent = new TicketEvent(
                event.ticketId(), event.subject(), event.description(), event.userEmail(),
                result.urgency().toString(), result.category(), result.reasoning(), event.createdAt(), now
        );

        triageEventProducer.publishRouted(classifiedEvent);
        log.info("Ticket {} classified as {}", event.ticketId(), result.urgency());
    }

    private void handleFailure(Ticket ticket, TicketEvent event, String failureReason) {
        Instant now = Instant.now();
        String note = "Processing failed: " + failureReason;

        try {
            ticket.appendStatusChange(TicketStatus.FAILED, note, now);
            ticketRepository.save(ticket);
        } catch (Exception mongoEx) {
            log.error("Could not persist FAILED status for ticket {} — Mongo write failed: {}",
                    event.ticketId(), mongoEx.getMessage());
        }

        TicketEvent dlqEvent = new TicketEvent(
                event.ticketId(), event.subject(), event.description(), event.userEmail(),
                null, null, note, event.createdAt(), now
        );
        triageEventProducer.publishRouted(dlqEvent);
    }


}
