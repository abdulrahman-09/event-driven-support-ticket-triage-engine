package com.am9.ticket_portal_service.service;

import com.am9.ticket_portal_service.dto.TicketDetailResponse;
import com.am9.ticket_portal_service.dto.TicketPageResponse;
import com.am9.ticket_portal_service.entity.Ticket;
import com.am9.ticket_portal_service.exception.InvalidTicketSortException;
import com.am9.ticket_portal_service.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TicketQueryService {

    private final TicketRepository ticketRepository;

    public TicketPageResponse listTickets(int page, int size, String sortBy, String direction){
        // To be done
        return null;
    }

    public TicketDetailResponse getTicket(String ticketId) {
        // To be done
        return null;
    }

    private SortSpec resolveSort(String sortBy, String direction){
        Sort.Direction sortDirection = parseSortDirection(direction);
        SortField sortField = resolveSortField(sortBy);

        Sort sort = Sort.by(sortDirection, sortField.documentProperty());

        if (!"createdAt".equals(sortField.documentProperty())){
            sort = sort.and(Sort.by(Sort.Direction.ASC, "createdAt"));
        }

        if (!"id".equals(sortField.documentProperty())) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        }
        return new SortSpec(
                sortField.apiName(),
                sortDirection.name().toLowerCase(Locale.ROOT),
                sort
        );
    }

    private Sort.Direction parseSortDirection(String direction){
        String normalized = normalize(direction, "desc");
        return switch (normalized){
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new InvalidTicketSortException(
                    "direction must be one of : asc, desc"
            );
        };
    }

    private SortField resolveSortField(String sortBy){
        String normalized = normalize(sortBy, "createdat");

        return switch (normalized){
            case "index", "id", "ticketid" -> new SortField("index", "id");
            case "status" -> new SortField("status", "status");
            case "createdat", "created", "createddate", "creationdate" ->
                    new SortField("createdAt", "createdAt");
            default -> throw new InvalidTicketSortException(
                    "sortBy must be one of: index, status, createdAt, creationDate"
            );
        };
    }


    private String normalize(String value, String defaultValue){
        if (value == null || value.isBlank()){
            return defaultValue;
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "");

    }

    private record SortField(String apiName, String documentProperty) {
    }

    private record SortSpec(String apiName, String direction, Sort sort) {
    }
}
