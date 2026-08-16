package com.am9.ticket_portal_service.controller;

import com.am9.ticket_portal_service.dto.TicketDetailResponse;
import com.am9.ticket_portal_service.dto.TicketPageResponse;
import com.am9.ticket_portal_service.service.TicketQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@Validated
@Tag(name = "Ticket portal", description = "Browse triaged support tickets and their status history.")
public class TicketPortalController {

    private final TicketQueryService ticketQueryService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "List support tickets",
            description = "Returns a stable, paginated list. Sort aliases are accepted and the response returns the canonical sort name."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket page returned successfully.",
                    content = @Content(schema = @Schema(implementation = TicketPageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid page, size, sortBy, or direction.")
    })
    public TicketPageResponse listTickets(
            @Parameter(description = "Zero-based page number.", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Number of tickets per page, from 1 to 100.", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(
                    description = "Sort by index (aliases: id, ticketId), status, or createdAt "
                            + "(aliases: created, createdDate, creationDate).",
                    example = "createdAt"
            )
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction: asc or desc.", example = "desc")
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return ticketQueryService.listTickets(page, size, sortBy, direction);
    }

    @GetMapping(value = "/{ticketId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get one support ticket", description = "Returns a triaged ticket with its chronological status history.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket found.",
                    content = @Content(schema = @Schema(implementation = TicketDetailResponse.class))),
            @ApiResponse(responseCode = "404", description = "No ticket has the supplied identifier.")
    })
    public TicketDetailResponse getTicket(
            @Parameter(description = "Ticket identifier assigned during ingestion.",
                    example = "2ee15339-21ce-4668-8bde-4d93b1fe8864")
            @PathVariable String ticketId) {
        return ticketQueryService.getTicket(ticketId);
    }
}
