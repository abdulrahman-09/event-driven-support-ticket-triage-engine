package com.am9.ticket_triage_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class TicketTriageServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketTriageServiceApplication.class, args);
	}

}
