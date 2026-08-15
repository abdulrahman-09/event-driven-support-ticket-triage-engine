package com.am9.ticket_triage_service.ai;

import com.am9.ticket_triage_service.dto.TriageResult;
import com.am9.ticket_triage_service.dto.ValidatedTriageResult;
import com.am9.ticket_triage_service.exception.InvalidTriageResultException;
import com.am9.ticket_triage_service.model.TicketStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TriageResultValidatorTest {

    private final TriageResultValidator validator = new TriageResultValidator();

    @Test
    void validate_rejectsAnEmptyAiResult() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidTriageResultException.class)
                .hasMessage("AI returned an empty triage result");
    }

    @ParameterizedTest
    @MethodSource("validUrgencies")
    void validate_normalizesAndMapsEverySupportedUrgency(String rawUrgency, TicketStatus expectedUrgency) {
        ValidatedTriageResult result = validator.validate(new TriageResult(
                rawUrgency, "Authentication", "Customer cannot complete sign-in."));

        assertThat(result.urgency()).isEqualTo(expectedUrgency);
        assertThat(result.category()).isEqualTo("Authentication");
        assertThat(result.reasoning()).isEqualTo("Customer cannot complete sign-in.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void validate_rejectsMissingUrgency(String urgency) {
        assertThatThrownBy(() -> validator.validate(new TriageResult(
                urgency, "Authentication", "Customer cannot complete sign-in.")))
                .isInstanceOf(InvalidTriageResultException.class)
                .hasMessage("AI returned missing urgency");
    }

    @ParameterizedTest
    @ValueSource(strings = {"HIGH", "STARTED", "FAILED"})
    void validate_rejectsUnsupportedUrgency(String urgency) {
        assertThatThrownBy(() -> validator.validate(new TriageResult(
                urgency, "Authentication", "Customer cannot complete sign-in.")))
                .isInstanceOf(InvalidTriageResultException.class)
                .hasMessage("AI returned unsupported urgency: " + urgency);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void validate_rejectsMissingCategory(String category) {
        assertThatThrownBy(() -> validator.validate(new TriageResult(
                "LOW", category, "Customer asks a question.")))
                .isInstanceOf(InvalidTriageResultException.class)
                .hasMessage("AI returned missing category");
    }

    @Test
    void validate_normalizesWhitespaceInCategoryAndReasoning() {
        ValidatedTriageResult result = validator.validate(new TriageResult(
                " critical ", "  Account\n\tAccess  ", "  Customer  cannot\n sign in.  "));

        assertThat(result.urgency()).isEqualTo(TicketStatus.CRITICAL);
        assertThat(result.category()).isEqualTo("Account Access");
        assertThat(result.reasoning()).isEqualTo("Customer cannot sign in.");
    }

    @Test
    void validate_acceptsCategoryAtTheNormalized50CharacterLimit() {
        String category = "x".repeat(50);

        ValidatedTriageResult result = validator.validate(new TriageResult(
                "MEDIUM", category, "A functional issue has a workaround."));

        assertThat(result.category()).isEqualTo(category);
    }

    @Test
    void validate_rejectsCategoryAt51NormalizedCharacters() {
        assertThatThrownBy(() -> validator.validate(new TriageResult(
                "MEDIUM", "x".repeat(51), "A functional issue has a workaround.")))
                .isInstanceOf(InvalidTriageResultException.class)
                .hasMessage("AI returned category longer than 50 characters");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void validate_rejectsMissingReasoning(String reasoning) {
        assertThatThrownBy(() -> validator.validate(new TriageResult(
                "LOW", "Question", reasoning)))
                .isInstanceOf(InvalidTriageResultException.class)
                .hasMessage("AI returned missing reasoning");
    }

    @Test
    void validate_acceptsReasoningAtTheNormalized500CharacterLimit() {
        String reasoning = "x".repeat(500);

        ValidatedTriageResult result = validator.validate(new TriageResult("LOW", "Question", reasoning));

        assertThat(result.reasoning()).isEqualTo(reasoning);
    }

    @Test
    void validate_rejectsReasoningAt501NormalizedCharacters() {
        assertThatThrownBy(() -> validator.validate(new TriageResult("LOW", "Question", "x".repeat(501))))
                .isInstanceOf(InvalidTriageResultException.class)
                .hasMessage("AI returned reasoning longer than 500 characters");
    }

    @Test
    void validate_measuresLengthAfterWhitespaceNormalization() {
        String categoryWithPadding = " " + "x".repeat(50) + " ";

        ValidatedTriageResult result = validator.validate(new TriageResult(
                "LOW", categoryWithPadding, "A question with enough context."));

        assertThat(result.category()).isEqualTo("x".repeat(50));
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> validUrgencies() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("critical", TicketStatus.CRITICAL),
                org.junit.jupiter.params.provider.Arguments.of(" MEDIUM ", TicketStatus.MEDIUM),
                org.junit.jupiter.params.provider.Arguments.of("low", TicketStatus.LOW));
    }
}
