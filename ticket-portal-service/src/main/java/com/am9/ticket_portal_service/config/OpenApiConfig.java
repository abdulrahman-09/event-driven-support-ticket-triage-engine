package com.am9.ticket_portal_service.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Ticket Portal API",
                version = "v1",
                description = "Browse triaged support tickets and their status history."
        ),
        servers = @Server(url = "http://localhost:8082", description = "Local development")
)
public class OpenApiConfig {
}
