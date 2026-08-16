package com.am9.ticket_portal_service.service;

import com.am9.ticket_portal_service.dto.TicketDetailResponse;
import com.am9.ticket_portal_service.dto.TicketPageResponse;
import com.am9.ticket_portal_service.entity.StatusChange;
import com.am9.ticket_portal_service.entity.Ticket;
import com.am9.ticket_portal_service.entity.TicketStatus;
import com.am9.ticket_portal_service.exception.InvalidTicketSortException;
import com.am9.ticket_portal_service.exception.TicketNotFoundException;
import com.am9.ticket_portal_service.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketQueryServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private TicketQueryService service;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    @Test
    void listTickets_returnsMappedFirstPageWithCreatedAtDefaultSort() {
        Page<Ticket> ticketPage = new PageImpl<>(
                List.of(ticket("ticket-1", "First"), ticket("ticket-2", "Second")),
                PageRequest.of(0, 20),
                2
        );
        when(ticketRepository.findAll(any(Pageable.class))).thenReturn(ticketPage);

        TicketPageResponse response = service.listTickets(0, 20, "createdAt", "desc");

        assertThat(response.tickets())
                .extracting(summary -> summary.index(), summary -> summary.id(), summary -> summary.subject())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, "ticket-1", "First"),
                        org.assertj.core.groups.Tuple.tuple(2L, "ticket-2", "Second")
                );
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.first()).isTrue();
        assertThat(response.last()).isTrue();
        assertThat(response.sortBy()).isEqualTo("createdAt");
        assertThat(response.direction()).isEqualTo("desc");

        verify(ticketRepository).findAll(pageableCaptor.capture());
        assertPageRequest(pageableCaptor.getValue(), 0, 20,
                order(Sort.Direction.DESC, "createdAt"),
                order(Sort.Direction.ASC, "id"));
    }

    @Test
    void listTickets_usesThePageOffsetForDisplayIndices() {
        Page<Ticket> ticketPage = new PageImpl<>(
                List.of(ticket("ticket-7", "Seventh"), ticket("ticket-8", "Eighth")),
                PageRequest.of(2, 3),
                11
        );
        when(ticketRepository.findAll(any(Pageable.class))).thenReturn(ticketPage);

        TicketPageResponse response = service.listTickets(2, 3, "status", "asc");

        assertThat(response.tickets()).extracting(summary -> summary.index())
                .containsExactly(7L, 8L);
        assertThat(response.totalPages()).isEqualTo(4);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isFalse();

        verify(ticketRepository).findAll(pageableCaptor.capture());
        assertPageRequest(pageableCaptor.getValue(), 2, 3,
                order(Sort.Direction.ASC, "status"),
                order(Sort.Direction.DESC, "createdAt"),
                order(Sort.Direction.ASC, "id"));
    }

    @Test
    void listTickets_returnsEmptyPageMetadataWithoutInventingTickets() {
        Page<Ticket> ticketPage = new PageImpl<>(List.of(), PageRequest.of(1, 5), 0);
        when(ticketRepository.findAll(any(Pageable.class))).thenReturn(ticketPage);

        TicketPageResponse response = service.listTickets(1, 5, "id", "asc");

        assertThat(response.tickets()).isEmpty();
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.sortBy()).isEqualTo("index");
        assertThat(response.direction()).isEqualTo("asc");

        verify(ticketRepository).findAll(pageableCaptor.capture());
        assertPageRequest(pageableCaptor.getValue(), 1, 5,
                order(Sort.Direction.ASC, "id"),
                order(Sort.Direction.DESC, "createdAt"));
    }

    @ParameterizedTest(name = "{0} {1} resolves to the index sort")
    @MethodSource("indexSortInputs")
    void listTickets_resolvesEveryIndexAlias(String sortBy, String direction, Sort.Direction expectedDirection) {
        stubEmptyPage();

        TicketPageResponse response = service.listTickets(0, 10, sortBy, direction);

        assertThat(response.sortBy()).isEqualTo("index");
        assertThat(response.direction()).isEqualTo(direction.toLowerCase());
        verify(ticketRepository).findAll(pageableCaptor.capture());
        assertPageRequest(pageableCaptor.getValue(), 0, 10,
                order(expectedDirection, "id"),
                order(Sort.Direction.DESC, "createdAt"));
    }

    @ParameterizedTest(name = "{0} {1} resolves to the createdAt sort")
    @MethodSource("createdAtSortInputs")
    void listTickets_resolvesEveryCreatedAtAliasAndNormalizesIt(
            String sortBy, String direction, Sort.Direction expectedDirection) {
        stubEmptyPage();

        TicketPageResponse response = service.listTickets(0, 10, sortBy, direction);

        assertThat(response.sortBy()).isEqualTo("createdAt");
        assertThat(response.direction()).isEqualTo(direction.toLowerCase());
        verify(ticketRepository).findAll(pageableCaptor.capture());
        assertPageRequest(pageableCaptor.getValue(), 0, 10,
                order(expectedDirection, "createdAt"),
                order(Sort.Direction.ASC, "id"));
    }

    @ParameterizedTest(name = "status {0} has both deterministic tie breakers")
    @MethodSource("directions")
    void listTickets_sortsByStatusInEitherDirection(String direction, Sort.Direction expectedDirection) {
        stubEmptyPage();

        TicketPageResponse response = service.listTickets(0, 10, "status", direction);

        assertThat(response.sortBy()).isEqualTo("status");
        assertThat(response.direction()).isEqualTo(direction.toLowerCase());
        verify(ticketRepository).findAll(pageableCaptor.capture());
        assertPageRequest(pageableCaptor.getValue(), 0, 10,
                order(expectedDirection, "status"),
                order(Sort.Direction.DESC, "createdAt"),
                order(Sort.Direction.ASC, "id"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void listTickets_defaultsMissingOrBlankSortByToCreatedAt(String sortBy) {
        stubEmptyPage();

        TicketPageResponse response = service.listTickets(0, 10, sortBy, "asc");

        assertThat(response.sortBy()).isEqualTo("createdAt");
        assertThat(response.direction()).isEqualTo("asc");
        verify(ticketRepository).findAll(pageableCaptor.capture());
        assertPageRequest(pageableCaptor.getValue(), 0, 10,
                order(Sort.Direction.ASC, "createdAt"),
                order(Sort.Direction.ASC, "id"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void listTickets_defaultsMissingOrBlankDirectionToDescending(String direction) {
        stubEmptyPage();

        TicketPageResponse response = service.listTickets(0, 10, "status", direction);

        assertThat(response.sortBy()).isEqualTo("status");
        assertThat(response.direction()).isEqualTo("desc");
        verify(ticketRepository).findAll(pageableCaptor.capture());
        assertPageRequest(pageableCaptor.getValue(), 0, 10,
                order(Sort.Direction.DESC, "status"),
                order(Sort.Direction.DESC, "createdAt"),
                order(Sort.Direction.ASC, "id"));
    }

    @Test
    void listTickets_rejectsUnsupportedSortBeforeCallingMongo() {
        assertThatThrownBy(() -> service.listTickets(0, 10, "priority", "asc"))
                .isInstanceOf(InvalidTicketSortException.class)
                .hasMessage("sortBy must be one of: index, status, createdAt, creationDate");

        verifyNoInteractions(ticketRepository);
    }

    @Test
    void listTickets_rejectsUnsupportedDirectionBeforeCallingMongo() {
        assertThatThrownBy(() -> service.listTickets(0, 10, "status", "up"))
                .isInstanceOf(InvalidTicketSortException.class)
                .hasMessage("direction must be one of : asc, desc");

        verifyNoInteractions(ticketRepository);
    }

    @Test
    void listTickets_propagatesRepositoryFailure() {
        IllegalStateException failure = new IllegalStateException("Mongo is unavailable");
        when(ticketRepository.findAll(any(Pageable.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.listTickets(0, 10, "status", "asc"))
                .isSameAs(failure);
    }

    @Test
    void getTicket_returnsAFullMappedDetail() {
        Ticket ticket = ticket("ticket-1", "Login problem");
        ticket.setStatusHistory(List.of(
                new StatusChange(TicketStatus.STARTED, Instant.parse("2026-01-01T00:00:00Z"), "Received"),
                new StatusChange(TicketStatus.MEDIUM, Instant.parse("2026-01-02T00:00:00Z"), "Triaged")
        ));
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket));

        TicketDetailResponse response = service.getTicket("ticket-1");

        assertThat(response.id()).isEqualTo("ticket-1");
        assertThat(response.subject()).isEqualTo("Login problem");
        assertThat(response.description()).isEqualTo("Description for Login problem");
        assertThat(response.customerEmail()).isEqualTo("customer@example.com");
        assertThat(response.status()).isEqualTo(TicketStatus.MEDIUM);
        assertThat(response.statusHistory()).extracting(change -> change.status(), change -> change.note())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(TicketStatus.STARTED, "Received"),
                        org.assertj.core.groups.Tuple.tuple(TicketStatus.MEDIUM, "Triaged")
                );
        verify(ticketRepository).findById("ticket-1");
    }

    @Test
    void getTicket_turnsNullStatusHistoryIntoAnEmptyResponseList() {
        Ticket ticket = ticket("ticket-1", "Login problem");
        ticket.setStatusHistory(null);
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket));

        TicketDetailResponse response = service.getTicket("ticket-1");

        assertThat(response.statusHistory()).isEmpty();
    }

    @Test
    void getTicket_throwsTheDomainExceptionForMissingTicket() {
        when(ticketRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTicket("missing"))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessage("No support ticket with id: missing");

        verify(ticketRepository).findById("missing");
    }

    @Test
    void getTicket_propagatesRepositoryFailure() {
        IllegalStateException failure = new IllegalStateException("Mongo is unavailable");
        when(ticketRepository.findById("ticket-1")).thenThrow(failure);

        assertThatThrownBy(() -> service.getTicket("ticket-1")).isSameAs(failure);
    }

    private void stubEmptyPage() {
        when(ticketRepository.findAll(any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(0);
                    return new PageImpl<Ticket>(List.of(), pageable, 0);
                });
    }

    private static Stream<Arguments> indexSortInputs() {
        return Stream.of("index", "id", "ticketId", "ticket_id")
                .flatMap(alias -> directions().map(direction -> Arguments.of(alias, direction.get()[0], direction.get()[1])));
    }

    private static Stream<Arguments> createdAtSortInputs() {
        return Stream.of("createdAt", "created", "createdDate", "creationDate", " CREATION-DATE ")
                .flatMap(alias -> directions().map(direction -> Arguments.of(alias, direction.get()[0], direction.get()[1])));
    }

    private static Stream<Arguments> directions() {
        return Stream.of(
                Arguments.of("ASC", Sort.Direction.ASC),
                Arguments.of("dEsC", Sort.Direction.DESC)
        );
    }

    private static Sort.Order order(Sort.Direction direction, String property) {
        return new Sort.Order(direction, property);
    }

    private static void assertPageRequest(Pageable pageable, int page, int size, Sort.Order... orders) {
        assertThat(pageable.getPageNumber()).isEqualTo(page);
        assertThat(pageable.getPageSize()).isEqualTo(size);
        assertThat(pageable.getSort().stream().toList()).containsExactly(orders);
    }

    private static Ticket ticket(String id, String subject) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setSubject(subject);
        ticket.setDescription("Description for " + subject);
        ticket.setCustomerEmail("customer@example.com");
        ticket.setStatus(TicketStatus.MEDIUM);
        ticket.setUrgencyReasoning("The issue affects one user");
        ticket.setCategory("ACCOUNT");
        ticket.setStatusHistory(new ArrayList<>());
        ticket.setCreatedAt(Instant.parse("2026-01-02T03:04:05Z"));
        ticket.setUpdatedAt(Instant.parse("2026-01-03T04:05:06Z"));
        return ticket;
    }
}
