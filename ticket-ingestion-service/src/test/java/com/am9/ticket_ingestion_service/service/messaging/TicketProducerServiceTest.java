package com.am9.ticket_ingestion_service.service.messaging;

import com.am9.ticket_ingestion_service.messaging.TicketEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketProducerServiceTest {

    private static final String TOPIC = "tickets.created.test";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private TicketProducerService producerService;

    @BeforeEach
    void setUp() {
        producerService = new TicketProducerService(kafkaTemplate);
        ReflectionTestUtils.setField(producerService, "ticketCreatedTopic", TOPIC);
    }

    @Test
    void publish_sendsEventWithItsTicketIdAsKafkaKey() {
        TicketEvent event = event();
        SendResult<String, Object> sendResult = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.partition()).thenReturn(2);
        when(metadata.offset()).thenReturn(42L);
        when(kafkaTemplate.send(TOPIC, event.ticketId(), event))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        CompletableFuture<SendResult<String, Object>> result = producerService.publishTicketCreated(event);

        assertThat(result.join()).isSameAs(sendResult);
        verify(kafkaTemplate).send(TOPIC, event.ticketId(), event);
    }

    @Test
    void publish_returnsAnExceptionalFutureWhenKafkaCompletesExceptionally() {
        TicketEvent event = event();
        IllegalStateException brokerFailure = new IllegalStateException("broker unavailable");
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(brokerFailure);
        when(kafkaTemplate.send(TOPIC, event.ticketId(), event)).thenReturn(failed);

        CompletableFuture<SendResult<String, Object>> result = producerService.publishTicketCreated(event);

        Throwable thrown = catchThrowable(result::join);

        assertThat(thrown)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(thrown.getCause()).isSameAs(brokerFailure);
        verify(kafkaTemplate).send(TOPIC, event.ticketId(), event);
    }

    @Test
    void publish_propagatesSynchronousKafkaTemplateFailure() {
        TicketEvent event = event();
        IllegalStateException failure = new IllegalStateException("template unavailable");
        when(kafkaTemplate.send(TOPIC, event.ticketId(), event)).thenThrow(failure);

        assertThatThrownBy(() -> producerService.publishTicketCreated(event)).isSameAs(failure);
    }

    @Test
    void publish_rejectsANullEventBeforeSending() {
        assertThatThrownBy(() -> producerService.publishTicketCreated(null))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void publish_returnsAnExceptionalFutureWhenKafkaSuppliesNoSendResult() {
        TicketEvent event = event();
        when(kafkaTemplate.send(eq(TOPIC), eq(event.ticketId()), eq(event)))
                .thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<SendResult<String, Object>> result = producerService.publishTicketCreated(event);

        assertThatThrownBy(result::join).isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(NullPointerException.class);
    }

    private TicketEvent event() {
        Instant now = Instant.parse("2026-01-02T03:04:05Z");
        return new TicketEvent("ticket-1", "Subject", "Description", "ana@example.com",
                null, null, null, now, now);
    }
}

