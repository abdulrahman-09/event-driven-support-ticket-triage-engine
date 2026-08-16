package com.am9.ticket_ingestion_service.controller;

import com.am9.ticket_ingestion_service.dto.CreateTicketRequest;
import com.am9.ticket_ingestion_service.dto.TicketResponse;
import com.am9.ticket_ingestion_service.exception.MissingIdempotencyKeyException;
import com.am9.ticket_ingestion_service.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@Tag(name = "Ticket ingestion", description = "Accept support tickets for asynchronous AI triage.")
public class TicketController {

    private final TicketService ticketService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Accept a support ticket",
            description = "Publishes a ticket creation event. Reuse the same Idempotency-Key only to retry the same request."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Ticket accepted for asynchronous triage.",
                    content = @Content(schema = @Schema(implementation = TicketResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or invalid idempotency key, or invalid request body."),
            @ApiResponse(responseCode = "409", description = "The idempotency key is processing or was used with a different request."),
            @ApiResponse(responseCode = "503", description = "The ticket-created event could not be published; retry the request.")
    })
    public ResponseEntity<TicketResponse> createTicket(
            @Parameter(
                    name = "Idempotency-Key",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "Caller-generated key, up to 128 characters. Reuse it only when retrying the same body.",
                    example = "b4d2d2d9-4b0a-4a78-a6e0-2c33bc035bbe"
            )
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Support request to submit for triage.",
                    content = @Content(schema = @Schema(implementation = CreateTicketRequest.class))
            )
            @Valid @RequestBody CreateTicketRequest request
    ){
        if (idempotencyKey == null || idempotencyKey.isBlank()){
            throw new MissingIdempotencyKeyException();
        }
        String normalizedIdempotencyKey = idempotencyKey.strip();
        TicketResponse response = ticketService.createTicket(normalizedIdempotencyKey, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
