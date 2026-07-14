package com.am9.ticket_triage_service.consumer;

import com.am9.ticket_triage_service.ai.TriageClassifier;
import com.am9.ticket_triage_service.dto.TicketEvent;
import com.am9.ticket_triage_service.dto.TriageResult;
import com.am9.ticket_triage_service.model.Ticket;
import com.am9.ticket_triage_service.model.TicketStatus;
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
//  private final TriageEventProducer eventProducer; To Be implemented

    @KafkaListener(topics = "${app.kafka.topic.tickets-created}", concurrency = "3")
    public void handleTicketCreated(TicketEvent event){
        Ticket ticket = Ticket.newFromEvent(
                event.ticketId(), event.subject(), event.description(),
                event.userEmail(), event.createdAt()
        );

        try{

            ticketRepository.save(ticket);
            TriageResult result = triageClassifier.classify(event);
            applyClassification(ticket, result, event);
        }catch (Exception ex){
            log.error("Failed to process ticket {}: {}", event.ticketId(), ex.getMessage());
//            handleFailure(ticket, event, ex.getMessage()); To be implemented
        }
    }

    private void applyClassification(Ticket ticket, TriageResult result, TicketEvent event) {
        TicketStatus newStatus = TicketStatus.valueOf(result.urgency().toUpperCase());
        Instant now = Instant.now();
        String note = "Classified as " + newStatus + ": " + result.reasoning();

        ticket.setCategory(result.category());
        ticket.setUrgencyReasoning(result.reasoning());
        ticket.appendStatusChange(newStatus, note, now);
        ticketRepository.save(ticket);
    }



}
