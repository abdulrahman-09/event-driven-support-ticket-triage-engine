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

    public CompletableFuture<SendResult<String, Object>> publishRouted(TicketEvent event){
        String topic = switch (event.urgency()){
            case "CRITICAL" -> criticalTopic;
            case "MEDIUM" -> mediumTopic;
            case "LOW" -> lowTopic;
            default -> dlqTopic;
        };

        return kafkaTemplate.send(topic, event.ticketId(), event);
    }
}
