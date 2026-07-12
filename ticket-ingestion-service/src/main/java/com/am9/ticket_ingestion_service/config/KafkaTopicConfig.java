package com.am9.ticket_ingestion_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${app.kafka.topic.tickets-created}")
    private String ticketCreatedTopic;

    @Bean
    public NewTopic ticketsCreatedTopic(){
        return TopicBuilder.name(ticketCreatedTopic).partitions(3).replicas(1).build();
    }
}