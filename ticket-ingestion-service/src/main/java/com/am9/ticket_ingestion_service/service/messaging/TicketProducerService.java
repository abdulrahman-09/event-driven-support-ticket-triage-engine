package com.am9.ticket_ingestion_service.service.messaging;


import com.am9.ticket_ingestion_service.messaging.TicketEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.tickets-created}")
    private String ticketCreatedTopic;

    public CompletableFuture<SendResult<String, Object>> publishTicketCreated(TicketEvent event) {
        return kafkaTemplate.send(ticketCreatedTopic, event.ticketId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ticket created event for ticketId={}", event.ticketId(), ex);
                    } else {
                        log.debug("Published ticket created event for ticketId={} to partition={} offset={}",
                                event.ticketId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

}
