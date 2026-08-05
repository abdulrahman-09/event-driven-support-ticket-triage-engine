package com.am9.ticket_portal_service.dto;

import java.util.List;

public record TicketPageResponse(
        List<TicketSummaryResponse> tickets,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        String sortBy,
        String direction
) {}
