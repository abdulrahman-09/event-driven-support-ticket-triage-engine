package com.am9.ticket_triage_service.producer;

import com.am9.ticket_triage_service.dto.TicketEvent;
import org.junit.jupiter.api.AfterEach;
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
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriageEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private TriageEventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new TriageEventProducer(kafkaTemplate);
        ReflectionTestUtils.setField(producer, "criticalTopic", "tickets.critical.test");
        ReflectionTestUtils.setField(producer, "mediumTopic", "tickets.medium.test");
        ReflectionTestUtils.setField(producer, "lowTopic", "tickets.low.test");
        ReflectionTestUtils.setField(producer, "dlqTopic", "tickets.dlq.test");
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void publishRouted_sendsEveryKnownUrgencyToItsTopic() {
        assertPublishedTo("CRITICAL", "tickets.critical.test");
        assertPublishedTo("MEDIUM", "tickets.medium.test");
        assertPublishedTo("LOW", "tickets.low.test");
    }

    @Test
    void publishRouted_sendsNullAndUnknownUrgencyToTheDlq() {
        assertPublishedTo(null, "tickets.dlq.test");
        assertPublishedTo("HIGH", "tickets.dlq.test");
    }

    @Test
    void publishRouted_returnsTheKafkaFutureUnchanged() {
        TicketEvent event = event("CRITICAL");
        CompletableFuture<SendResult<String, Object>> sendFuture = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send("tickets.critical.test", event.ticketId(), event)).thenReturn(sendFuture);

        CompletableFuture<SendResult<String, Object>> returned = producer.publishRouted(event);

        assertThat(returned).isSameAs(sendFuture);
    }

    @Test
    void publishRouted_propagatesSynchronousKafkaFailure() {
        TicketEvent event = event("LOW");
        IllegalStateException failure = new IllegalStateException("broker unavailable");
        when(kafkaTemplate.send("tickets.low.test", event.ticketId(), event)).thenThrow(failure);

        assertThatThrownBy(() -> producer.publishRouted(event)).isSameAs(failure);
    }

    @Test
    void publishRoutedAndAwait_returnsAfterASuccessfulSend() {
        TicketEvent event = event("MEDIUM");
        when(kafkaTemplate.send("tickets.medium.test", event.ticketId(), event))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishRoutedAndAwait(event);

        verify(kafkaTemplate).send("tickets.medium.test", event.ticketId(), event);
    }

    @Test
    void publishRoutedAndAwait_restoresInterruptStatusAndWrapsInterruptedException() {
        TicketEvent event = event("LOW");
        when(kafkaTemplate.send("tickets.low.test", event.ticketId(), event))
                .thenReturn(new InterruptingFuture<>("interrupted"));

        assertThatThrownBy(() -> producer.publishRoutedAndAwait(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Interrupted while publishing ticket ticket-1")
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void publishRoutedAndAwait_unwrapsExecutionExceptionCause() {
        TicketEvent event = event("LOW");
        IllegalArgumentException brokerFailure = new IllegalArgumentException("send failed");
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(brokerFailure);
        when(kafkaTemplate.send("tickets.low.test", event.ticketId(), event)).thenReturn(failedFuture);

        Throwable thrown = catchThrowable(() -> producer.publishRoutedAndAwait(event));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not publish ticket ticket-1");
        assertThat(thrown.getCause()).isSameAs(brokerFailure);
    }

    private void assertPublishedTo(String urgency, String expectedTopic) {
        TicketEvent event = event(urgency);
        when(kafkaTemplate.send(expectedTopic, event.ticketId(), event))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishRouted(event);

        verify(kafkaTemplate).send(expectedTopic, event.ticketId(), event);
    }

    private TicketEvent event(String urgency) {
        Instant now = Instant.parse("2026-01-02T03:04:05Z");
        return new TicketEvent("ticket-1", "Subject", "Description", "ana@example.com",
                urgency, "Account", "Customer needs help.", now, now);
    }

    private static final class InterruptingFuture<T> extends CompletableFuture<T> {
        private final String message;

        private InterruptingFuture(String message) {
            this.message = message;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            throw new InterruptedException(message);
        }
    }
}

