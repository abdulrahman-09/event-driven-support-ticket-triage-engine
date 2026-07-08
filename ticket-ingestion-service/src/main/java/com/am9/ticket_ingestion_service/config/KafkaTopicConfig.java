package com.am9.ticket_ingestion_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    @Value("${app.kafka.topic.tickets-created}")
    private String ticketsCreated;

    @Value("${app.kafka.topic.tickets-critical}")
    private String ticketsCritical;

    @Value("${app.kafka.topic.tickets-medium}")
    private String ticketsMedium;

    @Value("${app.kafka.topic.tickets-low}")
    private String ticketsLow;

    @Value("${app.kafka.topic.tickets-dlq}")
    private String ticketsDlq;

    @Bean
    public NewTopic ticketsCreatedTopic(){
        return TopicBuilder.name(ticketsCreated).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic ticketsCriticalTopic(){
        return TopicBuilder.name(ticketsCritical).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic ticketsMediumTopic(){
        return TopicBuilder.name(ticketsMedium).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic ticketsLowTopic(){
        return TopicBuilder.name(ticketsLow).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic ticketsDlqTopic(){
        return TopicBuilder.name(ticketsDlq).partitions(3).replicas(1).build();
    }
}