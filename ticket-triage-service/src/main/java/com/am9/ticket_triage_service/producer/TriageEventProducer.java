package com.am9.ticket_triage_service.producer;

import com.am9.ticket_triage_service.dto.TicketEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class TriageEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.tickets-critical}")
    private String criticalTopic;

    @Value("${app.kafka.topic.tickets-medium}")
    private String mediumTopic;

    @Value("${app.kafka.topic.tickets-low}")
    private String lowTopic;

    @Value("${app.kafka.topic.tickets-dlq}")
    private String dlqTopic;

    public CompletableFuture<SendResult<String, Object>> publishRouted(TicketEvent event) {
        String topic = (event.urgency() == null)
                ? dlqTopic
                : switch (event.urgency()) {
            case "CRITICAL" -> criticalTopic;
            case "MEDIUM" -> mediumTopic;
            case "LOW" -> lowTopic;
            default -> dlqTopic;
        };
        return kafkaTemplate.send(topic, event.ticketId(), event);
    }

    public void publishRoutedAndAwait(TicketEvent event) {
        try {
            publishRouted(event).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while publishing ticket " + event.ticketId(), ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            throw new IllegalStateException(
                    "Could not publish ticket " + event.ticketId(), ex.getCause());
        }
    }
}
