package com.am9.ticket_ingestion_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank(message = "subject must not be blank")
        @Size(max = 200, message = "subject must be 200 characters or fewer")
        String subject,

        @NotBlank(message = "description must not be blank")
        @Size(max = 5000, message = "description must be 5000 characters or fewer")
        String description,

        @NotBlank(message = "customerEmail must not be blank")
        @Email(message = "customerEmail must be a valid email address")
        String userEmail
) {}