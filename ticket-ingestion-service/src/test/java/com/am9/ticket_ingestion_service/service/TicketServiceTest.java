package com.am9.ticket_ingestion_service.service;

import com.am9.ticket_ingestion_service.dto.CreateTicketRequest;
import com.am9.ticket_ingestion_service.dto.TicketResponse;
import com.am9.ticket_ingestion_service.exception.DuplicateInFlightException;
import com.am9.ticket_ingestion_service.exception.TicketPublishFailedException;
import com.am9.ticket_ingestion_service.messaging.TicketEvent;
import com.am9.ticket_ingestion_service.service.idempotency.IdempotencyDecision;
import com.am9.ticket_ingestion_service.service.idempotency.IdempotencyService;
import com.am9.ticket_ingestion_service.service.messaging.TicketProducerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    private static final String IDEMPOTENCY_KEY = "request-1";
    private static final String REQUEST_HASH = "request-hash";

    @Mock
    private TicketProducerService producerService;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private TicketService ticketService;

    private final CreateTicketRequest request = new CreateTicketRequest(
            "Cannot sign in", "Reset link returns an error", "ana@example.com");

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void createTicket_returnsCachedResponseWithoutPublishing() {
        TicketResponse cached = response("cached-ticket");
        when(idempotencyService.fingerprint(request)).thenReturn(REQUEST_HASH);
        when(idempotencyService.startOrReturnCompleted(IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenReturn(IdempotencyDecision.returnCachedResponse(cached));

        TicketResponse result = ticketService.createTicket(IDEMPOTENCY_KEY, request);

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(producerService);
        verify(idempotencyService, never()).complete(any(), any(), any());
        verify(idempotencyService, never()).fail(any(), any(), any());
    }

    @Test
    void createTicket_publishesCompletesAndReturnsCorrelatedResponse() {
        prepareNewRequest();
        when(producerService.publishTicketCreated(any(TicketEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        TicketResponse result = ticketService.createTicket(IDEMPOTENCY_KEY, request);

        ArgumentCaptor<TicketEvent> eventCaptor = ArgumentCaptor.forClass(TicketEvent.class);
        verify(producerService).publishTicketCreated(eventCaptor.capture());
        TicketEvent event = eventCaptor.getValue();

        assertThat(result.ticketId()).isEqualTo(event.ticketId());
        assertThat(result.subject()).isEqualTo(request.subject());
        assertThat(result.description()).isEqualTo(request.description());
        assertThat(result.userEmail()).isEqualTo(request.userEmail());
        assertThat(result.createdAt()).isEqualTo(event.createdAt());
        assertThat(event.occurredAt()).isEqualTo(event.createdAt());
        assertThat(event.urgency()).isNull();
        assertThat(event.category()).isNull();
        assertThat(event.reasoning()).isNull();
        verify(idempotencyService).complete(IDEMPOTENCY_KEY, REQUEST_HASH, result);
        verify(idempotencyService, never()).fail(any(), any(), any());
    }

    @Test
    void createTicket_marksFailureAndRestoresInterruptStatus() {
        prepareNewRequest();
        when(producerService.publishTicketCreated(any(TicketEvent.class)))
                .thenReturn(new InterruptingFuture<>("broker wait interrupted"));

        TicketPublishFailedException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                TicketPublishFailedException.class,
                () -> ticketService.createTicket(IDEMPOTENCY_KEY, request));

        ArgumentCaptor<TicketEvent> eventCaptor = ArgumentCaptor.forClass(TicketEvent.class);
        verify(producerService).publishTicketCreated(eventCaptor.capture());
        assertThat(thrown.getTicketId()).isEqualTo(eventCaptor.getValue().ticketId());
        assertThat(thrown.getCause()).isInstanceOf(InterruptedException.class);
        verify(idempotencyService).fail(IDEMPOTENCY_KEY, REQUEST_HASH, "broker wait interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void createTicket_marksFailureWhenPublishFutureCompletesExceptionally() {
        prepareNewRequest();
        IllegalStateException brokerFailure = new IllegalStateException("broker unavailable");
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(brokerFailure);
        when(producerService.publishTicketCreated(any(TicketEvent.class))).thenReturn(failedFuture);

        TicketPublishFailedException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                TicketPublishFailedException.class,
                () -> ticketService.createTicket(IDEMPOTENCY_KEY, request));

        assertThat(thrown.getCause()).isInstanceOf(ExecutionException.class);
        verify(idempotencyService).fail(IDEMPOTENCY_KEY, REQUEST_HASH,
                ((ExecutionException) thrown.getCause()).getMessage());
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void createTicket_propagatesFingerprintFailureWithoutCallingOtherCollaborators() {
        IllegalStateException failure = new IllegalStateException("cannot fingerprint");
        when(idempotencyService.fingerprint(request)).thenThrow(failure);

        assertThatThrownBy(() -> ticketService.createTicket(IDEMPOTENCY_KEY, request))
                .isSameAs(failure);

        verifyNoInteractions(producerService);
        verify(idempotencyService, never()).startOrReturnCompleted(any(), any());
        verify(idempotencyService, never()).complete(any(), any(), any());
        verify(idempotencyService, never()).fail(any(), any(), any());
    }

    @Test
    void createTicket_propagatesClaimFailureWithoutPublishing() {
        when(idempotencyService.fingerprint(request)).thenReturn(REQUEST_HASH);
        DuplicateInFlightException failure = new DuplicateInFlightException(IDEMPOTENCY_KEY);
        when(idempotencyService.startOrReturnCompleted(IDEMPOTENCY_KEY, REQUEST_HASH)).thenThrow(failure);

        assertThatThrownBy(() -> ticketService.createTicket(IDEMPOTENCY_KEY, request))
                .isSameAs(failure);

        verifyNoInteractions(producerService);
        verify(idempotencyService, never()).complete(any(), any(), any());
        verify(idempotencyService, never()).fail(any(), any(), any());
    }

    @Test
    void createTicket_propagatesSynchronousProducerFailureWithoutMarkingFailure() {
        prepareNewRequest();
        IllegalStateException failure = new IllegalStateException("producer unavailable");
        when(producerService.publishTicketCreated(any(TicketEvent.class))).thenThrow(failure);

        assertThatThrownBy(() -> ticketService.createTicket(IDEMPOTENCY_KEY, request))
                .isSameAs(failure);

        verify(idempotencyService, never()).complete(any(), any(), any());
        verify(idempotencyService, never()).fail(any(), any(), any());
    }

    @Test
    void createTicket_propagatesCompletionPersistenceFailureAfterKafkaSuccess() {
        prepareNewRequest();
        when(producerService.publishTicketCreated(any(TicketEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        IllegalStateException cacheFailure = new IllegalStateException("cannot persist completion");
        doThrow(cacheFailure).when(idempotencyService).complete(eq(IDEMPOTENCY_KEY), eq(REQUEST_HASH), any());

        assertThatThrownBy(() -> ticketService.createTicket(IDEMPOTENCY_KEY, request))
                .isSameAs(cacheFailure);

        verify(idempotencyService, never()).fail(any(), any(), any());
    }

    @Test
    void createTicket_allowsFailurePersistenceExceptionToTakePrecedence() {
        prepareNewRequest();
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(producerService.publishTicketCreated(any(TicketEvent.class))).thenReturn(failedFuture);
        IllegalStateException cacheFailure = new IllegalStateException("cannot persist failure");
        doThrow(cacheFailure).when(idempotencyService).fail(eq(IDEMPOTENCY_KEY), eq(REQUEST_HASH), any());

        assertThatThrownBy(() -> ticketService.createTicket(IDEMPOTENCY_KEY, request))
                .isSameAs(cacheFailure);
    }

    private void prepareNewRequest() {
        when(idempotencyService.fingerprint(request)).thenReturn(REQUEST_HASH);
        when(idempotencyService.startOrReturnCompleted(IDEMPOTENCY_KEY, REQUEST_HASH))
                .thenReturn(IdempotencyDecision.processNewRequest());
    }

    private TicketResponse response(String ticketId) {
        return new TicketResponse(ticketId, request.subject(), request.description(), request.userEmail(),
                java.time.Instant.parse("2026-01-02T03:04:05Z"));
    }

    private static final class InterruptingFuture<T> extends CompletableFuture<T> {
        private final String message;

        private InterruptingFuture(String message) {
            this.message = message;
        }

        @Override
        public T get() throws InterruptedException {
            throw new InterruptedException(message);
        }
    }
}
