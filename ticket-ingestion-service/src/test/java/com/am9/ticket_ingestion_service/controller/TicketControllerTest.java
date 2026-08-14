package com.am9.ticket_ingestion_service.controller;

import com.am9.ticket_ingestion_service.dto.CreateTicketRequest;
import com.am9.ticket_ingestion_service.dto.TicketResponse;
import com.am9.ticket_ingestion_service.service.TicketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @Test
    void createTicket_returnsAcceptedResponseForAValidRequest() throws Exception {
        TicketResponse response = response();
        CreateTicketRequest request = validRequest();
        when(ticketService.createTicket("key-1", request)).thenReturn(response);

        mockMvc.perform(post("/api/v1/tickets")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.ticketId").value("ticket-1"))
                .andExpect(jsonPath("$.subject").value("Cannot sign in"))
                .andExpect(jsonPath("$.description").value("Reset link returns an error"))
                .andExpect(jsonPath("$.userEmail").value("ana@example.com"));

        verify(ticketService).createTicket("key-1", request);
    }

    @Test
    void createTicket_stripsTheIdempotencyKeyBeforeDelegating() throws Exception {
        TicketResponse response = response();
        CreateTicketRequest request = validRequest();
        when(ticketService.createTicket("key-1", request)).thenReturn(response);

        mockMvc.perform(post("/api/v1/tickets")
                        .header("Idempotency-Key", "  key-1  ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isAccepted());

        verify(ticketService).createTicket("key-1", request);
    }

    @Test
    void createTicket_rejectsAMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Idempotency-Key header is required"));

        verifyNoInteractions(ticketService);
    }

    @ParameterizedTest
    @MethodSource("blankHeaderValues")
    void createTicket_rejectsBlankIdempotencyKeys(String blankKey) throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Idempotency-Key", blankKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Idempotency-Key header is required"));

        verifyNoInteractions(ticketService);
    }

    @ParameterizedTest
    @MethodSource("blankValues")
    void createTicket_rejectsBlankSubjects(String subjectJson) throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWith("subject", subjectJson)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("subject: subject must not be blank"));

        verifyNoInteractions(ticketService);
    }

    @Test
    void createTicket_acceptsASubjectAtThe200CharacterLimit() throws Exception {
        String subject = "x".repeat(200);
        CreateTicketRequest request = new CreateTicketRequest(subject, "Reset link returns an error", "ana@example.com");
        when(ticketService.createTicket("key-1", request)).thenReturn(response());

        mockMvc.perform(post("/api/v1/tickets")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"%s","description":"Reset link returns an error","userEmail":"ana@example.com"}
                                """.formatted(subject)))
                .andExpect(status().isAccepted());

        verify(ticketService).createTicket("key-1", request);
    }

    @Test
    void createTicket_rejectsASubjectAt201Characters() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"%s","description":"Reset link returns an error","userEmail":"ana@example.com"}
                                """.formatted("x".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("subject: subject must be 200 characters or fewer"));

        verifyNoInteractions(ticketService);
    }

    @ParameterizedTest
    @MethodSource("blankValues")
    void createTicket_rejectsBlankDescriptions(String descriptionJson) throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWith("description", descriptionJson)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("description: description must not be blank"));

        verifyNoInteractions(ticketService);
    }

    @Test
    void createTicket_rejectsADescriptionAt5001Characters() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Cannot sign in","description":"%s","userEmail":"ana@example.com"}
                                """.formatted("x".repeat(5_001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("description: description must be 5000 characters or fewer"));

        verifyNoInteractions(ticketService);
    }

    @ParameterizedTest
    @MethodSource("blankValues")
    void createTicket_rejectsBlankEmails(String emailJson) throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWith("userEmail", emailJson)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(anyOf(
                        is("userEmail: customerEmail must not be blank"),
                        is("userEmail: customerEmail must be a valid email address"))));

        verifyNoInteractions(ticketService);
    }

    @Test
    void createTicket_rejectsMalformedEmail() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWith("userEmail", "\"not-an-email\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("userEmail: customerEmail must be a valid email address"));

        verifyNoInteractions(ticketService);
    }

    private static Stream<String> blankHeaderValues() {
        return Stream.of("", "   ", "\t\n");
    }

    private static Stream<String> blankValues() {
        return Stream.of("null", "\"\"", "\"   \"");
    }

    private CreateTicketRequest validRequest() {
        return new CreateTicketRequest("Cannot sign in", "Reset link returns an error", "ana@example.com");
    }

    private TicketResponse response() {
        return new TicketResponse("ticket-1", "Cannot sign in", "Reset link returns an error", "ana@example.com",
                Instant.parse("2026-01-02T03:04:05Z"));
    }

    private String validJson() {
        return """
                {"subject":"Cannot sign in","description":"Reset link returns an error","userEmail":"ana@example.com"}
                """;
    }

    private String jsonWith(String field, String jsonValue) {
        return switch (field) {
            case "subject" -> """
                    {"subject":%s,"description":"Reset link returns an error","userEmail":"ana@example.com"}
                    """.formatted(jsonValue);
            case "description" -> """
                    {"subject":"Cannot sign in","description":%s,"userEmail":"ana@example.com"}
                    """.formatted(jsonValue);
            case "userEmail" -> """
                    {"subject":"Cannot sign in","description":"Reset link returns an error","userEmail":%s}
                    """.formatted(jsonValue);
            default -> throw new IllegalArgumentException("Unexpected field: " + field);
        };
    }
}