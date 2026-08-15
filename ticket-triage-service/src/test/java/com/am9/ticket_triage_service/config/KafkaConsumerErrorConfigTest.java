package com.am9.ticket_triage_service.config;

import com.am9.ticket_triage_service.exception.InvalidTriageResultException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerErrorConfigTest {

    @Test
    void kafkaErrorHandler_createsAHandlerForTheConfiguredDlqPolicy() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

        DefaultErrorHandler handler = new KafkaConsumerErrorConfig()
                .kafkaErrorHandler(kafkaTemplate, "tickets.dlq.test");

        assertThat(handler).isNotNull();
        assertThat(handler.isAckAfterHandle()).isTrue();
        assertThat(handler.deliveryAttemptHeader()).isTrue();
        assertThat(new InvalidTriageResultException("invalid AI result"))
                .isInstanceOf(InvalidTriageResultException.class);
    }
}
