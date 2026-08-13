package com.am9.ticket_triage_service.config;

import com.am9.ticket_triage_service.exception.InvalidTriageResultException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerErrorConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topic.tickets-dlq}") String dlqTopic) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(dlqTopic, record.partition()));

        DefaultErrorHandler handler = new DefaultErrorHandler(
                recoverer, new FixedBackOff(1_000L, 2L));
        handler.addNotRetryableExceptions(InvalidTriageResultException.class);
        return handler;
    }
}