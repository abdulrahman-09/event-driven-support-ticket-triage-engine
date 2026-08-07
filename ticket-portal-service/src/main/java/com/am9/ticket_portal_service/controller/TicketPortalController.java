package com.am9.ticket_portal_service.controller;

import com.am9.ticket_portal_service.dto.TicketDetailResponse;
import com.am9.ticket_portal_service.dto.TicketPageResponse;
import com.am9.ticket_portal_service.dto.TicketSummaryResponse;
import com.am9.ticket_portal_service.service.TicketQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@Validated
public class TicketPortalController {

    private final TicketQueryService ticketQueryService;

    @GetMapping
    public TicketPageResponse listTickets(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return ticketQueryService.listTickets(page, size, sortBy, direction);
    }

    @GetMapping("/{ticketId}")
    public TicketDetailResponse getTicket(@PathVariable String ticketId) {
        return ticketQueryService.getTicket(ticketId);
    }
}
