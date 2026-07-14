package com.am9.ticket_triage_service.ai;

import com.am9.ticket_triage_service.dto.TicketEvent;
import com.am9.ticket_triage_service.dto.TriageResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
public class TriageClassifier {

    private static final String System_Prompt = """
            You are a support ticket triage assistant. Given a ticket's subject and
            description, classify it.

            Rules:
            - urgency must be exactly one of "CRITICAL", "MEDIUM", or "LOW" (uppercase,
              no other values)
            - CRITICAL means: service outage, data loss, security issue, payment failure,
              or the customer explicitly states they cannot use the product at all
            - MEDIUM means: a real functional problem that has a workaround, affects a
              non-blocking feature, or impacts the customer's workflow without stopping
              them entirely
            - LOW means: questions, minor cosmetic issues, feature requests, or anything
              described without urgency language
            - category should be a short 1-3 word label (e.g. "billing", "bug report",
              "feature request", "account access")
            - reasoning should be one concise sentence explaining the urgency call
            """;
    private final ChatClient chatClient;

    public TriageClassifier(ChatClient.Builder chatClientBuilder){
        this.chatClient = chatClientBuilder
                .defaultSystem(System_Prompt)
                .build();
    }

    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public TriageResult classify(TicketEvent event){
        String userPrompt = "Subject: %s\n\nDescription: %s"
                .formatted(event.subject(), event.description());
        return chatClient.prompt()
                .user(userPrompt)
                .call()
                .entity(TriageResult.class);
    }
}
