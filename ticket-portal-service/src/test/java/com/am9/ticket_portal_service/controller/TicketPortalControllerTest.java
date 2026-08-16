package com.am9.ticket_portal_service.controller;

import com.am9.ticket_portal_service.entity.StatusChange;
import com.am9.ticket_portal_service.entity.Ticket;
import com.am9.ticket_portal_service.entity.TicketStatus;
import com.am9.ticket_portal_service.exception.GlobalExceptionHandler;
import com.am9.ticket_portal_service.repository.TicketRepository;
import com.am9.ticket_portal_service.service.TicketQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketPortalController.class)
@Import({TicketQueryService.class, GlobalExceptionHandler.class, TicketPortalControllerTest.RepositoryTestConfiguration.class})
class TicketPortalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @AfterEach
    void resetRepository() {
        reset(ticketRepository);
    }

    @Test
    void listTickets_usesTheDeclaredDefaultsAndReturnsThePageJson() throws Exception {
        stubTicketsPage();

        mockMvc.perform(get("/api/v1/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets[0].index").value(1))
                .andExpect(jsonPath("$.tickets[0].id").value("ticket-1"))
                .andExpect(jsonPath("$.tickets[0].subject").value("Cannot log in"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.sortBy").value("createdAt"))
                .andExpect(jsonPath("$.direction").value("desc"));

        assertRepositorySort(
                new Sort.Order(Sort.Direction.DESC, "createdAt"),
                new Sort.Order(Sort.Direction.ASC, "id")
        );
    }

    @ParameterizedTest(name = "sortBy={0}, direction={1}")
    @MethodSource("validSortRequests")
    void listTickets_acceptsSortAliasesAndDirectionsThroughTheHttpEndpoint(
            String sortBy,
            String direction,
            String expectedApiSortBy,
            List<Sort.Order> expectedOrders) throws Exception {
        stubTicketsPage();

        mockMvc.perform(get("/api/v1/tickets")
                        .param("page", "2")
                        .param("size", "3")
                        .param("sortBy", sortBy)
                        .param("direction", direction))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.tickets[0].index").value(7))
                .andExpect(jsonPath("$.sortBy").value(expectedApiSortBy))
                .andExpect(jsonPath("$.direction").value(direction.toLowerCase()));

        assertRepositorySort(expectedOrders.toArray(Sort.Order[]::new));
    }

    @Test
    void listTickets_returnsBadRequestForUnsupportedSortWithoutQueryingMongo() throws Exception {
        mockMvc.perform(get("/api/v1/tickets")
                        .param("sortBy", "priority")
                        .param("direction", "asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("sortBy must be one of: index, status, createdAt, creationDate"))
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(ticketRepository);
    }

    @Test
    void listTickets_returnsBadRequestForUnsupportedDirectionWithoutQueryingMongo() throws Exception {
        mockMvc.perform(get("/api/v1/tickets")
                        .param("sortBy", "status")
                        .param("direction", "up"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("direction must be one of : asc, desc"));

        verifyNoInteractions(ticketRepository);
    }

    @ParameterizedTest
    @MethodSource("invalidPageRequests")
    void listTickets_rejectsOutOfRangePageParametersBeforeTheServiceRuns(String parameter, String value)
            throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param(parameter, value))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ticketRepository);
    }

    @ParameterizedTest
    @MethodSource("invalidTypeRequests")
    void listTickets_returnsAHelpfulErrorForNonNumericPageParameters(String parameter) throws Exception {
        mockMvc.perform(get("/api/v1/tickets").param(parameter, "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: " + parameter));

        verifyNoInteractions(ticketRepository);
    }

    @Test
    void getTicket_returnsTheFullTicketJson() throws Exception {
        Ticket ticket = ticket("ticket-1", "Cannot log in");
        ticket.setStatusHistory(List.of(
                new StatusChange(TicketStatus.STARTED, Instant.parse("2026-01-01T00:00:00Z"), "Received"),
                new StatusChange(TicketStatus.MEDIUM, Instant.parse("2026-01-02T00:00:00Z"), "Triaged")
        ));
        when(ticketRepository.findById("ticket-1")).thenReturn(Optional.of(ticket));

        mockMvc.perform(get("/api/v1/tickets/ticket-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ticket-1"))
                .andExpect(jsonPath("$.subject").value("Cannot log in"))
                .andExpect(jsonPath("$.description").value("The login page rejects valid credentials"))
                .andExpect(jsonPath("$.customerEmail").value("customer@example.com"))
                .andExpect(jsonPath("$.status").value("MEDIUM"))
                .andExpect(jsonPath("$.statusHistory[0].status").value("STARTED"))
                .andExpect(jsonPath("$.statusHistory[1].note").value("Triaged"));

        verify(ticketRepository).findById("ticket-1");
    }

    @Test
    void getTicket_returnsNotFoundWhenTheRepositoryDoesNotContainTheId() throws Exception {
        when(ticketRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/tickets/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("No support ticket with id: missing"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(ticketRepository).findById("missing");
    }

    private void stubTicketsPage() {
        when(ticketRepository.findAll(any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(0);
                    return new PageImpl<>(List.of(ticket("ticket-1", "Cannot log in")), pageable, 10);
                });
    }

    private void assertRepositorySort(Sort.Order... expectedOrders) {
        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(ticketRepository).findAll(captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getSort().stream().toList()).containsExactly(expectedOrders);
    }

    private static Stream<Arguments> validSortRequests() {
        return Stream.of(
                Arguments.of("ticketId", "ASC", "index", List.of(
                        new Sort.Order(Sort.Direction.ASC, "id"),
                        new Sort.Order(Sort.Direction.DESC, "createdAt"))),
                Arguments.of("status", "dEsC", "status", List.of(
                        new Sort.Order(Sort.Direction.DESC, "status"),
                        new Sort.Order(Sort.Direction.DESC, "createdAt"),
                        new Sort.Order(Sort.Direction.ASC, "id"))),
                Arguments.of("CREATION-DATE", "asc", "createdAt", List.of(
                        new Sort.Order(Sort.Direction.ASC, "createdAt"),
                        new Sort.Order(Sort.Direction.ASC, "id")))
        );
    }

    private static Stream<Arguments> invalidPageRequests() {
        return Stream.of(
                Arguments.of("page", "-1"),
                Arguments.of("size", "0"),
                Arguments.of("size", "101")
        );
    }

    private static Stream<Arguments> invalidTypeRequests() {
        return Stream.of(Arguments.of("page"), Arguments.of("size"));
    }

    private static Ticket ticket(String id, String subject) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setSubject(subject);
        ticket.setDescription("The login page rejects valid credentials");
        ticket.setCustomerEmail("customer@example.com");
        ticket.setStatus(TicketStatus.MEDIUM);
        ticket.setUrgencyReasoning("The customer cannot access the account");
        ticket.setCategory("ACCOUNT");
        ticket.setCreatedAt(Instant.parse("2026-01-02T03:04:05Z"));
        ticket.setUpdatedAt(Instant.parse("2026-01-03T04:05:06Z"));
        return ticket;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RepositoryTestConfiguration {

        @Bean
        TicketRepository ticketRepository() {
            return org.mockito.Mockito.mock(TicketRepository.class);
        }
    }
}
