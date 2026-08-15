package com.am9.ticket_triage_service.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerConfigTest {

    @Test
    void producerFactory_configuresADurableJsonProducer() {
        KafkaProducerConfig config = configuredConfig();

        ProducerFactory<String, Object> producerFactory = config.producerFactory();

        assertThat(producerFactory).isInstanceOf(DefaultKafkaProducerFactory.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = ((DefaultKafkaProducerFactory<String, Object>) producerFactory)
                .getConfigurationProperties();
        assertThat(properties).containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka.example:9092")
                .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10_000)
                .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000)
                .containsEntry(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 500);
    }

    @Test
    void kafkaTemplate_isCreatedForTheSuppliedProducerFactory() {
        KafkaProducerConfig config = configuredConfig();
        ProducerFactory<String, Object> producerFactory = config.producerFactory();

        KafkaTemplate<String, Object> template = config.kafkaTemplate(producerFactory);

        assertThat(template.getProducerFactory()).isSameAs(producerFactory);
    }

    private KafkaProducerConfig configuredConfig() {
        KafkaProducerConfig config = new KafkaProducerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServer", "kafka.example:9092");
        ReflectionTestUtils.setField(config, "deliveryTimeoutMs", 10_000);
        ReflectionTestUtils.setField(config, "requestTimeoutMs", 5_000);
        ReflectionTestUtils.setField(config, "retryBackoffMs", 500);
        return config;
    }
}
