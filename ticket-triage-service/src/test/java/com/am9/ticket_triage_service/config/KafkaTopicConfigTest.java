package com.am9.ticket_triage_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicConfigTest {

    private KafkaTopicConfig config;

    @BeforeEach
    void setUp() {
        config = new KafkaTopicConfig();
        ReflectionTestUtils.setField(config, "ticketsCritical", "tickets.critical.test");
        ReflectionTestUtils.setField(config, "ticketsMedium", "tickets.medium.test");
        ReflectionTestUtils.setField(config, "ticketsLow", "tickets.low.test");
        ReflectionTestUtils.setField(config, "ticketsDlq", "tickets.dlq.test");
    }

    @Test
    void everyTopicHasItsConfiguredNameAndExpectedTopology() {
        Stream.of(
                        new TopicExpectation("tickets.critical.test", config::ticketsCriticalTopic),
                        new TopicExpectation("tickets.medium.test", config::ticketsMediumTopic),
                        new TopicExpectation("tickets.low.test", config::ticketsLowTopic),
                        new TopicExpectation("tickets.dlq.test", config::ticketsDlqTopic))
                .forEach(expectation -> {
                    NewTopic topic = expectation.topic().get();
                    assertThat(topic.name()).isEqualTo(expectation.name());
                    assertThat(topic.numPartitions()).isEqualTo(3);
                    assertThat(topic.replicationFactor()).isEqualTo((short) 1);
                });
    }

    private record TopicExpectation(String name, Supplier<NewTopic> topic) {
    }
}

