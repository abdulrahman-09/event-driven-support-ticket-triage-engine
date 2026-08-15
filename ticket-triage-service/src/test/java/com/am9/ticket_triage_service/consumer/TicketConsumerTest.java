package com.am9.ticket_triage_service.consumer;

import com.am9.ticket_triage_service.ai.TriageClassifier;
import com.am9.ticket_triage_service.ai.TriageResultValidator;
import com.am9.ticket_triage_service.dto.TicketEvent;
import com.am9.ticket_triage_service.dto.TriageResult;
import com.am9.ticket_triage_service.dto.ValidatedTriageResult;
import com.am9.ticket_triage_service.exception.InvalidTicketEventException;
import com.am9.ticket_triage_service.exception.InvalidTriageResultException;
import com.am9.ticket_triage_service.model.StatusChange;
import com.am9.ticket_triage_service.model.Ticket;
import com.am9.ticket_triage_service.model.TicketStatus;
import com.am9.ticket_triage_service.producer.TriageEventProducer;
import com.am9.ticket_triage_service.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketConsumerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-02T03:04:05Z");
    private static final TicketEvent EVENT = new TicketEvent(
            "ticket-1", "Cannot sign in", "Reset link returns an error", "ana@example.com",
            null, null, null, CREATED_AT, CREATED_AT);

    @Mock
    private TriageClassifier triageClassifier;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TriageEventProducer triageEventProducer;

    @Mock
    private TriageResultValidator triageResultValidator;

    private TicketConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TicketConsumer(triageClassifier, ticketRepository, triageEventProducer, triageResultValidator);
    }

    @ParameterizedTest
    @MethodSource("invalidEvents")
    void handleTicketCreated_rejectsEveryInvalidEventBeforeUsingCollaborators(TicketEvent invalidEvent) {
        assertThatThrownBy(() -> consumer.handleTicketCreated(invalidEvent))
                .isInstanceOf(InvalidTicketEventException.class)
                .hasMessage("Invalid tickets.created event");

        verifyNoInteractions(triageClassifier, ticketRepository, triageEventProducer, triageResultValidator);
    }

    @Test
    void handleTicketCreated_acceptsNullClassificationFieldsOnAnOtherwiseValidInboundEvent() {
        Ticket ticket = Ticket.newFromEvent(EVENT.ticketId(), EVENT.subject(), EVENT.description(), EVENT.userEmail(), CREATED_AT);
        ticket.setRoutePublished(true);
        when(ticketRepository.findById(EVENT.ticketId())).thenReturn(Optional.of(ticket));

        consumer.handleTicketCreated(EVENT);

        verify(ticketRepository).findById(EVENT.ticketId());
        verifyNoInteractions(triageClassifier, triageResultValidator, triageEventProducer);
    }

    @Test
    void handleTicketCreated_insertsANewTicketFromTheInboundEvent() {
        TriageResult rawResult = new TriageResult("LOW", "Authentication", "Customer needs sign-in help.");
        ValidatedTriageResult validated = new ValidatedTriageResult(
                TicketStatus.LOW, "Authentication", "Customer needs sign-in help.");
        when(ticketRepository.findById(EVENT.ticketId())).thenReturn(Optional.empty());
        when(ticketRepository.insert(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(triageClassifier.classify(EVENT)).thenReturn(rawResult);
        when(triageResultValidator.validate(rawResult)).thenReturn(validated);

        consumer.handleTicketCreated(EVENT);

        ArgumentCaptor<Ticket> inserted = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).insert(inserted.capture());
        Ticket ticket = inserted.getValue();
        assertThat(ticket.getId()).isEqualTo(EVENT.ticketId());
        assertThat(ticket.getSubject()).isEqualTo(EVENT.subject());
        assertThat(ticket.getDescription()).isEqualTo(EVENT.description());
        assertThat(ticket.getCustomerEmail()).isEqualTo(EVENT.userEmail());
        assertThat(ticket.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.LOW);
        assertThat(ticket.isRoutePublished()).isTrue();
        assertThat(ticket.getStatusHistory()).extracting(StatusChange::status)
                .containsExactly(TicketStatus.STARTED, TicketStatus.LOW);
    }

    @Test
    void handleTicketCreated_reusesTheWinningTicketAfterADuplicateInsertRace() {
        Ticket winner = routedTicket(TicketStatus.CRITICAL);
        DuplicateKeyException duplicate = new DuplicateKeyException("ticket already inserted");
        when(ticketRepository.findById(EVENT.ticketId())).thenReturn(Optional.empty(), Optional.of(winner));
        when(ticketRepository.insert(any(Ticket.class))).thenThrow(duplicate);

        consumer.handleTicketCreated(EVENT);

        verify(ticketRepository, times(2)).findById(EVENT.ticketId());
        verify(ticketRepository).insert(any(Ticket.class));
        verifyNoInteractions(triageClassifier, triageResultValidator, triageEventProducer);
    }

    @Test
    void handleTicketCreated_propagatesTheDuplicateWhenTheWinnerCannotBeRead() {
        DuplicateKeyException duplicate = new DuplicateKeyException("ticket already inserted");
        when(ticketRepository.findById(EVENT.ticketId())).thenReturn(Optional.empty(), Optional.empty());
        when(ticketRepository.insert(any(Ticket.class))).thenThrow(duplicate);

        assertThatThrownBy(() -> consumer.handleTicketCreated(EVENT)).isSameAs(duplicate);

        verify(ticketRepository, times(2)).findById(EVENT.ticketId());
        verifyNoInteractions(triageClassifier, triageResultValidator, triageEventProducer);
    }

    @Test
    void handleTicketCreated_propagatesAnInitialRepositoryLookupFailure() {
        IllegalStateException failure = new IllegalStateException("Mongo unavailable");
        when(ticketRepository.findById(EVENT.ticketId())).thenThrow(failure);

        assertThatThrownBy(() -> consumer.handleTicketCreated(EVENT)).isSameAs(failure);

        verifyNoInteractions(triageClassifier, triageResultValidator, triageEventProducer);
    }

    @Test
    void handleTicketCreated_ignoresAReplayAfterTheRouteWasPublished() {
        Ticket ticket = routedTicket(TicketStatus.MEDIUM);
        when(ticketRepository.findById(EVENT.ticketId())).thenReturn(Optional.of(ticket));

        consumer.handleTicketCreated(EVENT);

        assertThat(ticket.isRoutePublished()).isTrue();
        verifyNoInteractions(triageClassifier, triageResultValidator, triageEventProducer);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"CRITICAL", "MEDIUM", "LOW"})
    void handleTicketCreated_routesAPreviouslyClassifiedTicketWithoutCallingAi(TicketStatus status) {
        Ticket ticket = classifiedTicket(status);
        when(ticketRepository.findById(EVENT.ticketId())).thenReturn(Optional.of(ticket));

        consumer.handleTicketCreated(EVENT);

        ArgumentCaptor<TicketEvent> routed = ArgumentCaptor.forClass(TicketEvent.class);
        verify(triageEventProducer).publishRoutedAndAwait(routed.capture());
        assertRoutedEventMatchesTicket(routed.getValue(), ticket, status);
        assertThat(ticket.isRoutePublished()).isTrue();
        verify(ticketRepository).save(ticket);
        verifyNoInteractions(triageClassifier, triageResultValidator);
    }

    @Test
    void handleTicketCreated_rejectsAFailedTicketBeforePublishing() {
        Ticket ticket = classifiedTicket(TicketStatus.FAILED);
        when(ticketRepository.findById(EVENT.ticketId())).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> consumer.handleTicketCreated(EVENT))
                .isInstanceOf(InvalidTriageResultException.class)
                .hasMessage("Ticket ticket-1 is not in a routable status");

        verifyNoInteractions(triageClassifier, triageResultValidator, triageEventProducer);
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void handleTicketCreated_rejectsATicketWithoutAStatusBeforePublishing() {
        Ticket ticket = Ticket.newFromEvent(EVENT.ticketId(), EVENT.subject(), EVENT.description(), EVENT.userEmail(), CREATED_AT);
        ticket.setStatus(null);
        when(ticketRepository.findById(EVENT.ticketId())).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> consumer.handleTicketCreated(EVENT))
                .isInstanceOf(InvalidTriageResultException.class)
                .hasMessage("Ticket ticket-1 is not in a routable status");

        verifyNoInteractions(triageClassifier, triageResultValidator, triageEventProducer);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"CRITICAL", "MEDIUM", "LOW"})
    void handleTicketCreated_classifiesPersistsRoutesAndMarksEveryValidUrgencyPublished(TicketStatus status) {
        Ticket ticket = Ticket.newFromEvent(EVENT.ticketId(), EVENT.subject(), EVENT.description(), EVENT.userEmail(), CREATED_AT);
        TriageResult rawResult = new TriageResult(status.name(), "Authentication", "Customer cannot sign in.");
        ValidatedTriageResult validated = new ValidatedTriageResult(status, "Authentication", "Customer cannot sign in.");
        when(ticketRepository.findById(EVENT.ticketId())).thenReturn(Optional.of(ticket));
        when(triageClassifier.classify(EVENT)).thenReturn(rawResult);
        when(triageResultValidator.validate(rawResult)).thenReturn(validated);

        consumer.handleTicketCreated(EVENT);

        assertThat(ticket.getStatus()).isEqualTo(status);
        assertThat(ticket.getCategory()).isEqualTo("Authentication");
        assertThat(ticket.getUrgencyReasoning()).isEqualTo("Customer cannot sign in.");
        assertThat(ticket.getStatusHistory()).last().isEqualTo(new StatusChange(status,
                ticket.getUpdatedAt(), "Classified as " + status + ": Customer cannot sign in."));
        assertThat(ticket.isRoutePublished()).isTrue();
        ArgumentCaptor<TicketEvent> routed = ArgumentCaptor.forClass(TicketEvent.class);
        InOrder order = inOrder(ticketRepository, triageEventProducer);
        order.verify(ticketRepository).save(ticket);
        order.verify(triageEventProducer).publishRoutedAndAwait(routed.capture());
        order.verify(ticketRepository).save(ticket);
        assertRoutedEventMatchesTicket(routed.getValue(), ticket, status);
    }

    @Test
    void handleTicketCreated_marksTicketFailedWhenTheClassifierReturnsAnInvalidResult() {
        Ticket ticket = Ticket.newFromEvent(EVENT.ticketId(), EVENT.subject(), EVENT.description(), EVENT.userEmail(), CREATED_AT);
        InvalidTriageResultException invalid = new InvalidTriageResultException("AI result is malformed");
        when(ticketRepository.findById(EVENT.ticketId())).thenReturn(Optional.of(ticket));
        when(triageClassifier.classify(EVENT)).thenThrow(invalid);

        assertThatThrownBy(() -> consumer.handleTicketCreated(EVENT)).isSameAs(invalid);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.FAILED);
        assertThat(ticket.getStatusHistory()).last().extracting(StatusChange::note)
                .isEqualTo("Triage produced an invalid response");
        verify(ticketRepository).save(ticket);
        verifyNoInteractions(triageEventProducer);
    }

    @Test
    void handleTicketCreated_marksTicketFailedWhenTheValidatorRejectsAiOutput() {
        Ticket ticket = Ticket.newFromEvent(EVENT.ticketId(), EVENT.subject(), EVENT.description(), EVENT.userEmail(), CREATED_AT);
        TriageResult rawResult = new TriageResult("HIGH", "Authentication", "Not valid.");
        InvalidTriageResultException invalid = new InvalidTriageResultException("unsupported urgency");
        when(ticketRepository.findById(EVENT.ticketId())).thenReturn(Optional.of(ticket));
        when(triageClassifier.classify(EVENT)).thenReturn(rawResult);
        when(triageResultValidator.validate(rawResult)).thenThrow(invalid);

        assertThatThrownBy(() -> consumer.handleTicketCreated(EVENT)).isSameAs(invalid);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.FAILED);
        verify(ticketRepository).save(ticket);
        verifyNoInteractions(triageEventProducer);
    }

    @Test
    void handleTicketCreated_propagatesAiTransportFailureWithoutMarkingTheTicketFailed() {
        Ticket ticket = Ticket.newFromEvent(EVENT.ticketId(), EVENT.subject(), EVENT.description(), EVENT.userEmail(), CREATED_AT);
        IllegalStateException failure = new IllegalStateException("Gemini timeout");
        when(ticketRepository.findById(EVENT.ticketId())).thenReturn(Optional.of(ticket));
        when(triageClassifier.classify(EVENT)).thenThrow(failure);

        assertThatThrownBy(() -> consumer.handleTicketCreated(EVENT)).isSameAs(failure);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.STARTED);
        verify(ticketRepository, never()).save(any(Ticket.class));
        verifyNoInteractions(triageEventProducer);
    }

    @Test
    void handleTicketCreated_stopsBeforeRoutingWhenClassificationPersistenceFails() {
        Ticket ticket = Ticket.newFromEvent(EVENT.ticketId(), EVENT.subject(), EVENT.description(), EVENT.userEmail(), CREATED_AT);
        arrangeValidClassification(ticket, TicketStatus.CRITICAL);
        IllegalStateException persistenceFailure = new IllegalStateException("Mongo write failed");
        doThrow(persistenceFailure).when(ticketRepository).save(ticket);

        assertThatThrownBy(() -> consumer.handleTicketCreated(EVENT)).isSameAs(persistenceFailure);

        assertThat(ticket.isRoutePublished()).isFalse();
        verifyNoInteractions(triageEventProducer);
    }

    @Test
    void handleTicketCreated_keepsRouteUnpublishedWhenRoutingFails() {
        Ticket ticket = Ticket.newFromEvent(EVENT.ticketId(), EVENT.subject(), EVENT.description(), EVENT.userEmail(), CREATED_AT);
        arrangeValidClassification(ticket, TicketStatus.MEDIUM);
        IllegalStateException publishFailure = new IllegalStateException("Kafka unavailable");
        doThrow(publishFailure).when(triageEventProducer).publishRoutedAndAwait(any(TicketEvent.class));

        assertThatThrownBy(() -> consumer.handleTicketCreated(EVENT)).isSameAs(publishFailure);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.MEDIUM);
        assertThat(ticket.isRoutePublished()).isFalse();
        verify(ticketRepository).save(ticket);
    }

    @Test
    void handleTicketCreated_propagatesTheFinalPersistenceFailureAfterPublishing() {
        Ticket ticket = Ticket.newFromEvent(EVENT.ticketId(), EVENT.subject(), EVENT.description(), EVENT.userEmail(), CREATED_AT);
        arrangeValidClassification(ticket, TicketStatus.LOW);
        IllegalStateException persistenceFailure = new IllegalStateException("Mongo final save failed");
        when(ticketRepository.save(ticket)).thenReturn(ticket).thenThrow(persistenceFailure);

        assertThatThrownBy(() -> consumer.handleTicketCreated(EVENT)).isSameAs(persistenceFailure);

        assertThat(ticket.isRoutePublished()).isTrue();
        verify(triageEventProducer).publishRoutedAndAwait(any(TicketEvent.class));
        verify(ticketRepository, times(2)).save(ticket);
    }

    private void arrangeValidClassification(Ticket ticket, TicketStatus status) {
        TriageResult raw = new TriageResult(status.name(), "Authentication", "Customer cannot sign in.");
        when(ticketRepository.findById(EVENT.ticketId())).thenReturn(Optional.of(ticket));
        when(triageClassifier.classify(EVENT)).thenReturn(raw);
        when(triageResultValidator.validate(raw)).thenReturn(
                new ValidatedTriageResult(status, "Authentication", "Customer cannot sign in."));
    }

    private Ticket classifiedTicket(TicketStatus status) {
        Ticket ticket = Ticket.newFromEvent(EVENT.ticketId(), EVENT.subject(), EVENT.description(), EVENT.userEmail(), CREATED_AT);
        ticket.setCategory("Authentication");
        ticket.setUrgencyReasoning("Customer cannot sign in.");
        ticket.appendStatusChange(status, "Previously classified", CREATED_AT.plusSeconds(1));
        return ticket;
    }

    private Ticket routedTicket(TicketStatus status) {
        Ticket ticket = classifiedTicket(status);
        ticket.setRoutePublished(true);
        return ticket;
    }

    private void assertRoutedEventMatchesTicket(TicketEvent routed, Ticket ticket, TicketStatus status) {
        assertThat(routed.ticketId()).isEqualTo(ticket.getId());
        assertThat(routed.subject()).isEqualTo(ticket.getSubject());
        assertThat(routed.description()).isEqualTo(ticket.getDescription());
        assertThat(routed.userEmail()).isEqualTo(ticket.getCustomerEmail());
        assertThat(routed.urgency()).isEqualTo(status.name());
        assertThat(routed.category()).isEqualTo(ticket.getCategory());
        assertThat(routed.reasoning()).isEqualTo(ticket.getUrgencyReasoning());
        assertThat(routed.createdAt()).isEqualTo(ticket.getCreatedAt());
        assertThat(routed.occurredAt()).isNotNull().isAfterOrEqualTo(CREATED_AT);
    }

    private static Stream<TicketEvent> invalidEvents() {
        return Stream.of(
                null,
                new TicketEvent(null, "Subject", "Description", "ana@example.com", null, null, null, CREATED_AT, CREATED_AT),
                new TicketEvent("", "Subject", "Description", "ana@example.com", null, null, null, CREATED_AT, CREATED_AT),
                new TicketEvent("   ", "Subject", "Description", "ana@example.com", null, null, null, CREATED_AT, CREATED_AT),
                new TicketEvent("ticket-1", null, "Description", "ana@example.com", null, null, null, CREATED_AT, CREATED_AT),
                new TicketEvent("ticket-1", "", "Description", "ana@example.com", null, null, null, CREATED_AT, CREATED_AT),
                new TicketEvent("ticket-1", "   ", "Description", "ana@example.com", null, null, null, CREATED_AT, CREATED_AT),
                new TicketEvent("ticket-1", "Subject", null, "ana@example.com", null, null, null, CREATED_AT, CREATED_AT),
                new TicketEvent("ticket-1", "Subject", "", "ana@example.com", null, null, null, CREATED_AT, CREATED_AT),
                new TicketEvent("ticket-1", "Subject", "   ", "ana@example.com", null, null, null, CREATED_AT, CREATED_AT),
                new TicketEvent("ticket-1", "Subject", "Description", null, null, null, null, CREATED_AT, CREATED_AT),
                new TicketEvent("ticket-1", "Subject", "Description", "", null, null, null, CREATED_AT, CREATED_AT),
                new TicketEvent("ticket-1", "Subject", "Description", "   ", null, null, null, CREATED_AT, CREATED_AT),
                new TicketEvent("ticket-1", "Subject", "Description", "ana@example.com", null, null, null, null, CREATED_AT));
    }
}

