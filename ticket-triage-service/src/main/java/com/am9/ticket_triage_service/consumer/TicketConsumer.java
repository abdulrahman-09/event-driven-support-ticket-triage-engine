package com.am9.ticket_triage_service.consumer;

import com.am9.ticket_triage_service.ai.TriageClassifier;
import com.am9.ticket_triage_service.ai.TriageResultValidator;
import com.am9.ticket_triage_service.dto.TicketEvent;
import com.am9.ticket_triage_service.dto.TriageResult;
import com.am9.ticket_triage_service.dto.ValidatedTriageResult;
import com.am9.ticket_triage_service.exception.InvalidTicketEventException;
import com.am9.ticket_triage_service.exception.InvalidTriageResultException;
import com.am9.ticket_triage_service.model.Ticket;
import com.am9.ticket_triage_service.model.TicketStatus;
import com.am9.ticket_triage_service.producer.TriageEventProducer;
import com.am9.ticket_triage_service.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
    public void handleTicketCreated(TicketEvent event) {
        validateEvent(event);
        Ticket ticket = loadOrCreate(event);

        if (ticket.isRoutePublished()) {
            log.info("Ticket {} was already routed; ignoring replay", event.ticketId());
            return;
        }

        if (ticket.getStatus() == TicketStatus.STARTED) {
            classifyAndPersist(ticket, event);
        }

        if (!isRoutable(ticket.getStatus())) {
            throw new InvalidTriageResultException(
                    "Ticket " + event.ticketId() + " is not in a routable status");
        }

        triageEventProducer.publishRoutedAndAwait(toRoutedEvent(ticket, event));
        ticket.setRoutePublished(true);
        ticketRepository.save(ticket);
        log.info("Ticket {} routed as {}", event.ticketId(), ticket.getStatus());
    }

    private Ticket loadOrCreate(TicketEvent event) {
        return ticketRepository.findById(event.ticketId()).orElseGet(() -> {
            try {
                return ticketRepository.insert(Ticket.newFromEvent(
                        event.ticketId(), event.subject(), event.description(),
                        event.userEmail(), event.createdAt()));
            } catch (DuplicateKeyException duplicate) {
                return ticketRepository.findById(event.ticketId())
                        .orElseThrow(() -> duplicate);
            }
        });
    }

    private void classifyAndPersist(Ticket ticket, TicketEvent event) {
        try {
            TriageResult rawResult = triageClassifier.classify(event);
            ValidatedTriageResult result = triageResultValidator.validate(rawResult);
            Instant now = Instant.now();

            ticket.setCategory(result.category());
            ticket.setUrgencyReasoning(result.reasoning());
            ticket.appendStatusChange(result.urgency(),
                    "Classified as " + result.urgency() + ": " + result.reasoning(), now);
            ticketRepository.save(ticket);
        } catch (InvalidTriageResultException invalidResult) {
            ticket.appendStatusChange(TicketStatus.FAILED,
                    "Triage produced an invalid response", Instant.now());
            ticketRepository.save(ticket);
            throw invalidResult;
        }
    }

    private TicketEvent toRoutedEvent(Ticket ticket, TicketEvent source) {
        return new TicketEvent(ticket.getId(), ticket.getSubject(), ticket.getDescription(),
                ticket.getCustomerEmail(), ticket.getStatus().name(), ticket.getCategory(),
                ticket.getUrgencyReasoning(), ticket.getCreatedAt(), Instant.now());
    }

    private boolean isRoutable(TicketStatus status) {
        return status == TicketStatus.CRITICAL
                || status == TicketStatus.MEDIUM
                || status == TicketStatus.LOW;
    }

    private void validateEvent(TicketEvent event) {
        if (event == null || isBlank(event.ticketId()) || isBlank(event.subject())
                || isBlank(event.description()) || isBlank(event.userEmail())
                || event.createdAt() == null) {
            throw new InvalidTicketEventException("Invalid tickets.created event");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
