package com.am9.ticket_ingestion_service.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Ticket Ingestion API",
                version = "v1",
                description = "Accepts support tickets for asynchronous triage."
        ),
        servers = @Server(url = "http://localhost:8080", description = "Local development")
)
public class OpenApiConfig {
}