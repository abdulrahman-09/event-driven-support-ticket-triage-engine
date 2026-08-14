package com.am9.ticket_ingestion_service.exception;

import com.am9.ticket_ingestion_service.dto.CreateTicketRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidation_returnsTheFirstFieldError() throws Exception {
        BindingResult bindingResult = bindingResult();
        bindingResult.addError(new FieldError("request", "subject", "subject must not be blank"));

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(validationException(bindingResult));

        assertError(response, HttpStatus.BAD_REQUEST, "subject: subject must not be blank");
    }

    @Test
    void handleValidation_usesFallbackWhenNoFieldErrorExists() throws Exception {
        ResponseEntity<Map<String, Object>> response = handler.handleValidation(validationException(bindingResult()));

        assertError(response, HttpStatus.BAD_REQUEST, "Validation failed");
    }

    @Test
    void handleNotFound_mapsTo404() {
        assertError(handler.handleNotFound(new TicketNotFoundException("ticket-1")), HttpStatus.NOT_FOUND,
                "No ticket found with id: ticket-1");
    }

    @Test
    void handleDuplicateInFlight_mapsTo409WithoutLeakingTheKey() {
        assertError(handler.handleDuplicateInFlight(new DuplicateInFlightException("secret-key")), HttpStatus.CONFLICT,
                "A request with this Idempotency-Key is still processing.");
    }

    @Test
    void handleIdempotencyKeyConflict_mapsTo409() {
        assertError(handler.handleIdempotencyKeyConflict(new IdempotencyKeyConflictException()), HttpStatus.CONFLICT,
                "Idempotency-Key was already used with a different request body");
    }

    @Test
    void handleMissingKey_mapsTo400() {
        assertError(handler.handleMissingKey(new MissingIdempotencyKeyException()), HttpStatus.BAD_REQUEST,
                "Idempotency-Key header is required");
    }

    @Test
    void handleInvalidKey_mapsTo400() {
        assertError(handler.handleInvalidIdempotencyKey(new InvalidIdempotencyKeyException("key too long")),
                HttpStatus.BAD_REQUEST, "key too long");
    }

    @Test
    void handleTicketPublishFailed_mapsTo503WithoutLeakingInfrastructureDetails() {
        assertError(handler.handleTicketPublishFailed(new TicketPublishFailedException("ticket-1",
                        new IllegalStateException("Kafka timeout"))),
                HttpStatus.SERVICE_UNAVAILABLE, "Ticket could not be accepted right now. Please retry.");
    }

    @Test
    void exceptionConstructors_exposeTheirDocumentedData() {
        DuplicateInFlightException duplicate = new DuplicateInFlightException("key-1");
        TicketPublishFailedException publishFailure = new TicketPublishFailedException("ticket-1",
                new IllegalArgumentException("cause"));

        assertThat(duplicate.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(duplicate).hasMessage("Request already in progress for this idempotency key");
        assertThat(publishFailure.getTicketId()).isEqualTo("ticket-1");
        assertThat(publishFailure).hasMessage("Failed to publish ticket created event for ticketId: ticket-1")
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(new IdempotencyKeyConflictException())
                .hasMessage("Idempotency-Key was already used with a different request body");
        assertThat(new MissingIdempotencyKeyException()).hasMessage("Idempotency-Key header is required");
        assertThat(new InvalidIdempotencyKeyException("reason")).hasMessage("reason");
        assertThat(new TicketNotFoundException("ticket-1")).hasMessage("No ticket found with id: ticket-1");
    }

    private MethodArgumentNotValidException validationException(BindingResult bindingResult) throws Exception {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("request", CreateTicketRequest.class);
        return new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
    }

    @SuppressWarnings("unused")
    private static void request(CreateTicketRequest request) {
    }

    private BindingResult bindingResult() {
        return new BeanPropertyBindingResult(new Object(), "request");
    }

    private void assertError(ResponseEntity<Map<String, Object>> response, HttpStatus expectedStatus, String expectedMessage) {
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        assertThat(response.getBody()).containsEntry("status", expectedStatus.value())
                .containsEntry("error", expectedStatus.getReasonPhrase())
                .containsEntry("message", expectedMessage);
        assertThat(response.getBody().get("timestamp")).isInstanceOf(Instant.class);
    }
}

