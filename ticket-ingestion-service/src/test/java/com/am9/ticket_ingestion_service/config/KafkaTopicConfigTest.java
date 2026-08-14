package com.am9.ticket_ingestion_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigTest {

    @Test
    void ticketsCreatedTopic_hasTheConfiguredNameAndExpectedTopology() {
        KafkaTopicConfig config = new KafkaTopicConfig();
        ReflectionTestUtils.setField(config, "ticketCreatedTopic", "tickets.created.test");

        NewTopic topic = config.ticketsCreatedTopic();

        assertThat(topic.name()).isEqualTo("tickets.created.test");
        assertThat(topic.numPartitions()).isEqualTo(3);
        assertThat(topic.replicationFactor()).isEqualTo((short) 1);
    }
}
