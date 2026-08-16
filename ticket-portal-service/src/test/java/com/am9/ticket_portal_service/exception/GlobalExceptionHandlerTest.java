package com.am9.ticket_portal_service.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleInvalidTicketSort_returnsBadRequestErrorBody() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleInvalidTicketSort(new InvalidTicketSortException("bad sort"));

        assertErrorBody(response, HttpStatus.BAD_REQUEST, "bad sort");
    }

    @Test
    void handleTicketNotFound_returnsNotFoundErrorBody() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleTicketNotFound(new TicketNotFoundException("ticket-9"));

        assertErrorBody(response, HttpStatus.NOT_FOUND, "No support ticket with id: ticket-9");
    }

    @Test
    void handleConstraintViolation_includesTheFirstViolationPathAndMessage() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("listTickets.size");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be less than or equal to 100");

        ResponseEntity<Map<String, Object>> response = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of(violation))
        );

        assertErrorBody(response, HttpStatus.BAD_REQUEST,
                "listTickets.size: must be less than or equal to 100");
    }

    @Test
    void handleConstraintViolation_usesFallbackMessageWhenThereAreNoViolations() {
        ResponseEntity<Map<String, Object>> response = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of())
        );

        assertErrorBody(response, HttpStatus.BAD_REQUEST, "Validation failed");
    }

    @Test
    void handleTypeMismatch_identifiesTheInvalidParameter() {
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "page", null, new IllegalArgumentException("not a number")
        );

        ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(exception);

        assertErrorBody(response, HttpStatus.BAD_REQUEST, "Invalid value for parameter: page");
    }

    private static void assertErrorBody(
            ResponseEntity<Map<String, Object>> response,
            HttpStatus expectedStatus,
            String expectedMessage) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody())
                .containsEntry("status", expectedStatus.value())
                .containsEntry("error", expectedStatus.getReasonPhrase())
                .containsEntry("message", expectedMessage);
        assertThat(response.getBody().get("timestamp")).isInstanceOf(Instant.class);
    }
}
