package com.am9.ticket_ingestion_service.service.idempotency;

import com.am9.ticket_ingestion_service.dto.CreateTicketRequest;
import com.am9.ticket_ingestion_service.dto.TicketResponse;
import com.am9.ticket_ingestion_service.exception.DuplicateInFlightException;
import com.am9.ticket_ingestion_service.exception.IdempotencyKeyConflictException;
import com.am9.ticket_ingestion_service.exception.InvalidIdempotencyKeyException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final String KEY = "key-1";
    private static final String REQUEST_HASH = "request-hash";
    private static final Duration NORMAL_TTL = Duration.ofSeconds(3_600);
    private static final Duration FAILED_TTL = Duration.ofSeconds(30);
    private static final String REDIS_PREFIX = "idempotency:create-ticket:";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private IdempotencyService service;

    private final CreateTicketRequest request = new CreateTicketRequest(
            "Cannot sign in", "Reset link returns an error", "ana@example.com");

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = newService(objectMapper);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void fingerprint_isDeterministicAndUsesTheEndpointAndCanonicalJson() throws Exception {
        String expectedCanonicalValue = "POST /api/v1/tickets\n" + objectMapper.writeValueAsString(request);

        String first = service.fingerprint(request);
        String second = service.fingerprint(request);

        assertThat(first).isEqualTo(sha256(expectedCanonicalValue));
        assertThat(second).isEqualTo(first).matches("[0-9a-f]{64}");
    }

    @ParameterizedTest
    @MethodSource("changedRequests")
    void fingerprint_changesWhenTheRequestBodyChanges(CreateTicketRequest changedRequest) {
        assertThat(service.fingerprint(changedRequest)).isNotEqualTo(service.fingerprint(request));
    }

    @Test
    void fingerprint_wrapsJsonSerializationFailure() throws Exception {
        JsonProcessingException jsonFailure = new JsonProcessingException("cannot serialize request") { };
        ObjectMapper failingMapper = mapperThatFailsToWrite(jsonFailure);
        IdempotencyService failingService = newService(failingMapper);

        Throwable thrown = catchThrowable(() -> failingService.fingerprint(request));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to create idempotency request fingerprint");
        assertThat(thrown.getCause()).isSameAs(jsonFailure);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void start_rejectsNullEmptyAndBlankKeys(String invalidKey) {
        assertThatThrownBy(() -> service.startOrReturnCompleted(invalidKey, REQUEST_HASH))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key must not be blank");

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void start_rejectsAKeyLongerThan128Utf16CodeUnits() {
        assertThatThrownBy(() -> service.startOrReturnCompleted("x".repeat(129), REQUEST_HASH))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("Idempotency-Key must be 128 characters or fewer");

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void start_acceptsAKeyAtThe128CodeUnitBoundaryAndClaimsIt() throws Exception {
        String keyAtLimit = "x".repeat(128);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        IdempotencyDecision decision = service.startOrReturnCompleted(keyAtLimit, REQUEST_HASH);

        assertThat(decision.shouldProcess()).isTrue();
        ArgumentCaptor<String> serializedRecord = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(
                org.mockito.ArgumentMatchers.eq(redisKey(keyAtLimit)), serializedRecord.capture(),
                org.mockito.ArgumentMatchers.eq(NORMAL_TTL));
        assertThat(objectMapper.readValue(serializedRecord.getValue(), IdempotencyRecord.class).status())
                .isEqualTo(IdempotencyStatus.PROCESSING);
    }

    @Test
    void start_claimsANewKeyAndStoresAProcessingRecord() throws Exception {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        IdempotencyDecision decision = service.startOrReturnCompleted(KEY, REQUEST_HASH);

        assertThat(decision).isEqualTo(IdempotencyDecision.processNewRequest());
        ArgumentCaptor<String> serializedRecord = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(
                org.mockito.ArgumentMatchers.eq(redisKey(KEY)), serializedRecord.capture(),
                org.mockito.ArgumentMatchers.eq(NORMAL_TTL));
        IdempotencyRecord stored = objectMapper.readValue(serializedRecord.getValue(), IdempotencyRecord.class);
        assertThat(stored.requestHash()).isEqualTo(REQUEST_HASH);
        assertThat(stored.status()).isEqualTo(IdempotencyStatus.PROCESSING);
        assertThat(stored.response()).isNull();
        assertThat(stored.errorMessage()).isNull();
        assertThat(stored.createdAt()).isEqualTo(stored.updatedAt());
        verify(valueOperations, never()).get(anyString());
    }

    @ParameterizedTest
    @MethodSource("occupiedClaimResults")
    void start_treatsFalseAndNullClaimResultsAsAnOccupiedKey(Boolean claimResult) throws Exception {
        TicketResponse cached = response("cached-ticket");
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(claimResult);
        when(valueOperations.get(redisKey(KEY))).thenReturn(json(completedRecord(cached)));

        IdempotencyDecision decision = service.startOrReturnCompleted(KEY, REQUEST_HASH);

        assertThat(decision.shouldProcess()).isFalse();
        assertThat(decision.cachedResponse()).isEqualTo(cached);
    }

    @Test
    void start_retriesWhenTheOccupiedRecordDisappearsThenClaimsIt() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false, true);
        when(valueOperations.get(redisKey(KEY))).thenReturn(null);

        IdempotencyDecision decision = service.startOrReturnCompleted(KEY, REQUEST_HASH);

        assertThat(decision).isEqualTo(IdempotencyDecision.processNewRequest());
        verify(valueOperations, times(2)).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(valueOperations).get(redisKey(KEY));
    }

    @Test
    void start_throwsAfterThreeDisappearingRecordsWithoutAFourthRedisAttempt() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false, false, false);
        when(valueOperations.get(redisKey(KEY))).thenReturn(null, null, null);

        assertThatThrownBy(() -> service.startOrReturnCompleted(KEY, REQUEST_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could not be resolved after 3 attempts");

        verify(valueOperations, times(3)).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(valueOperations, times(3)).get(redisKey(KEY));
    }

    @Test
    void start_returnsTheCompletedCachedResponse() throws Exception {
        TicketResponse cached = response("cached-ticket");
        stubOccupied(completedRecord(cached));

        IdempotencyDecision decision = service.startOrReturnCompleted(KEY, REQUEST_HASH);

        assertThat(decision.shouldProcess()).isFalse();
        assertThat(decision.cachedResponse()).isEqualTo(cached);
    }

    @Test
    void start_returnsANullCachedResponseForACorruptCompletedRecord() throws Exception {
        stubOccupied(new IdempotencyRecord(REQUEST_HASH, IdempotencyStatus.COMPLETED, null, null,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")));

        IdempotencyDecision decision = service.startOrReturnCompleted(KEY, REQUEST_HASH);

        assertThat(decision.shouldProcess()).isFalse();
        assertThat(decision.cachedResponse()).isNull();
    }

    @Test
    void start_allowsARepeatForAMatchingFailedRecord() throws Exception {
        stubOccupied(new IdempotencyRecord(REQUEST_HASH, IdempotencyStatus.FAILED, null, "broker failure",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:01:00Z")));

        assertThat(service.startOrReturnCompleted(KEY, REQUEST_HASH))
                .isEqualTo(IdempotencyDecision.processNewRequest());
    }

    @Test
    void start_rejectsAMatchingProcessingRecordAsDuplicateInFlight() throws Exception {
        stubOccupied(IdempotencyRecord.processing(REQUEST_HASH, Instant.parse("2026-01-01T00:00:00Z")));

        assertThatThrownBy(() -> service.startOrReturnCompleted(KEY, REQUEST_HASH))
                .isInstanceOf(DuplicateInFlightException.class)
                .hasMessage("Request already in progress for this idempotency key")
                .extracting("idempotencyKey").isEqualTo(KEY);
    }

    @Test
    void start_rejectsARecordCreatedForADifferentRequestBody() throws Exception {
        stubOccupied(new IdempotencyRecord("different-hash", IdempotencyStatus.COMPLETED, response("ticket-1"), null,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")));

        assertThatThrownBy(() -> service.startOrReturnCompleted(KEY, REQUEST_HASH))
                .isInstanceOf(IdempotencyKeyConflictException.class)
                .hasMessage("Idempotency-Key was already used with a different request body");
    }

    @Test
    void start_wrapsMalformedStoredJson() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(valueOperations.get(redisKey(KEY))).thenReturn("not json");

        assertThatThrownBy(() -> service.startOrReturnCompleted(KEY, REQUEST_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to deserialize idempotency record")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void start_wrapsProcessingRecordSerializationFailure() throws Exception {
        JsonProcessingException jsonFailure = new JsonProcessingException("cannot serialize record") { };
        ObjectMapper failingMapper = mapperThatFailsToWrite(jsonFailure);
        IdempotencyService failingService = newService(failingMapper);

        Throwable thrown = catchThrowable(() -> failingService.startOrReturnCompleted(KEY, REQUEST_HASH));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to serialize idempotency record");
        assertThat(thrown.getCause()).isSameAs(jsonFailure);
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void start_propagatesRedisClaimFailure() {
        IllegalStateException redisFailure = new IllegalStateException("Redis unavailable");
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenThrow(redisFailure);

        assertThatThrownBy(() -> service.startOrReturnCompleted(KEY, REQUEST_HASH))
                .isSameAs(redisFailure);
    }

    @Test
    void start_exposesACorruptRecordWithANullRequestHash() throws Exception {
        stubOccupied(new IdempotencyRecord(null, IdempotencyStatus.PROCESSING, null, null,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")));

        assertThatThrownBy(() -> service.startOrReturnCompleted(KEY, REQUEST_HASH))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void complete_rejectsInvalidKeysBeforeCallingRedis(String invalidKey) {
        assertThatThrownBy(() -> service.complete(invalidKey, REQUEST_HASH, response("ticket-1")))
                .isInstanceOf(InvalidIdempotencyKeyException.class);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void complete_createsACompletedRecordWhenTheOriginalEntryExpired() throws Exception {
        TicketResponse response = response("ticket-1");
        when(valueOperations.get(redisKey(KEY))).thenReturn(null);

        service.complete(KEY, REQUEST_HASH, response);

        IdempotencyRecord stored = capturedSetRecord(NORMAL_TTL);
        assertThat(stored.requestHash()).isEqualTo(REQUEST_HASH);
        assertThat(stored.status()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(stored.response()).isEqualTo(response);
        assertThat(stored.errorMessage()).isNull();
        assertThat(stored.createdAt()).isEqualTo(stored.updatedAt());
    }

    @ParameterizedTest
    @EnumSource(IdempotencyStatus.class)
    void complete_transitionsAnyMatchingStoredStateToCompleted(IdempotencyStatus initialStatus) throws Exception {
        Instant originalCreatedAt = Instant.parse("2026-01-01T00:00:00Z");
        TicketResponse response = response("ticket-2");
        IdempotencyRecord existing = new IdempotencyRecord(REQUEST_HASH, initialStatus, response("old-ticket"), "old error",
                originalCreatedAt, Instant.parse("2026-01-01T00:01:00Z"));
        when(valueOperations.get(redisKey(KEY))).thenReturn(json(existing));

        service.complete(KEY, REQUEST_HASH, response);

        IdempotencyRecord stored = capturedSetRecord(NORMAL_TTL);
        assertThat(stored.status()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(stored.requestHash()).isEqualTo(REQUEST_HASH);
        assertThat(stored.response()).isEqualTo(response);
        assertThat(stored.errorMessage()).isNull();
        assertThat(stored.createdAt()).isEqualTo(originalCreatedAt);
        assertThat(stored.updatedAt()).isAfter(originalCreatedAt);
    }

    @Test
    void complete_rethrowsARequestHashConflictWithoutWriting() throws Exception {
        when(valueOperations.get(redisKey(KEY))).thenReturn(json(new IdempotencyRecord("other-hash",
                IdempotencyStatus.PROCESSING, null, null, Instant.now(), Instant.now())));

        assertThatThrownBy(() -> service.complete(KEY, REQUEST_HASH, response("ticket-1")))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void complete_wrapsRedisReadFailureAfterTheEventWasPublished() {
        IllegalStateException redisFailure = new IllegalStateException("read unavailable");
        when(valueOperations.get(redisKey(KEY))).thenThrow(redisFailure);

        Throwable thrown = catchThrowable(() -> service.complete(KEY, REQUEST_HASH, response("ticket-1")));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to persist completed idempotency record");
        assertThat(thrown.getCause()).isSameAs(redisFailure);
    }

    @Test
    void complete_wrapsMalformedStoredJson() {
        when(valueOperations.get(redisKey(KEY))).thenReturn("not json");

        Throwable thrown = catchThrowable(() -> service.complete(KEY, REQUEST_HASH, response("ticket-1")));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to persist completed idempotency record")
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(thrown.getCause().getCause()).isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void complete_wrapsSerializationFailure() throws Exception {
        JsonProcessingException jsonFailure = new JsonProcessingException("cannot serialize completion") { };
        ObjectMapper failingMapper = mapperThatFailsToWrite(jsonFailure);
        IdempotencyService failingService = newService(failingMapper);
        when(valueOperations.get(redisKey(KEY))).thenReturn(null);

        Throwable thrown = catchThrowable(() -> failingService.complete(KEY, REQUEST_HASH, response("ticket-1")));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to persist completed idempotency record")
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(thrown.getCause().getCause()).isSameAs(jsonFailure);
    }

    @Test
    void complete_wrapsRedisWriteFailure() {
        IllegalStateException redisFailure = new IllegalStateException("write unavailable");
        when(valueOperations.get(redisKey(KEY))).thenReturn(null);
        doThrow(redisFailure).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        Throwable thrown = catchThrowable(() -> service.complete(KEY, REQUEST_HASH, response("ticket-1")));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to persist completed idempotency record");
        assertThat(thrown.getCause()).isSameAs(redisFailure);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void fail_rejectsInvalidKeysBeforeCallingRedis(String invalidKey) {
        assertThatThrownBy(() -> service.fail(invalidKey, REQUEST_HASH, "failure"))
                .isInstanceOf(InvalidIdempotencyKeyException.class);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void fail_createsAFailedRecordWhenTheOriginalEntryExpired() throws Exception {
        when(valueOperations.get(redisKey(KEY))).thenReturn(null);

        service.fail(KEY, REQUEST_HASH, "broker unavailable");

        IdempotencyRecord stored = capturedSetRecord(FAILED_TTL);
        assertThat(stored.requestHash()).isEqualTo(REQUEST_HASH);
        assertThat(stored.status()).isEqualTo(IdempotencyStatus.FAILED);
        assertThat(stored.response()).isNull();
        assertThat(stored.errorMessage()).isEqualTo("broker unavailable");
        assertThat(stored.createdAt()).isEqualTo(stored.updatedAt());
    }

    @ParameterizedTest
    @EnumSource(IdempotencyStatus.class)
    void fail_transitionsAnyExistingStateAndPreservesItsOriginalHash(IdempotencyStatus initialStatus) throws Exception {
        Instant originalCreatedAt = Instant.parse("2026-01-01T00:00:00Z");
        IdempotencyRecord existing = new IdempotencyRecord("stored-hash", initialStatus, response("old-ticket"), "old error",
                originalCreatedAt, Instant.parse("2026-01-01T00:01:00Z"));
        when(valueOperations.get(redisKey(KEY))).thenReturn(json(existing));

        service.fail(KEY, REQUEST_HASH, "broker unavailable");

        IdempotencyRecord stored = capturedSetRecord(FAILED_TTL);
        assertThat(stored.requestHash()).isEqualTo("stored-hash");
        assertThat(stored.status()).isEqualTo(IdempotencyStatus.FAILED);
        assertThat(stored.response()).isNull();
        assertThat(stored.errorMessage()).isEqualTo("broker unavailable");
        assertThat(stored.createdAt()).isEqualTo(originalCreatedAt);
        assertThat(stored.updatedAt()).isAfter(originalCreatedAt);
    }

    @Test
    void fail_propagatesMalformedStoredJsonWithoutWrappingIt() {
        when(valueOperations.get(redisKey(KEY))).thenReturn("not json");

        assertThatThrownBy(() -> service.fail(KEY, REQUEST_HASH, "failure"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to deserialize idempotency record")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void fail_propagatesRedisReadFailureWithoutWrappingIt() {
        IllegalStateException redisFailure = new IllegalStateException("read unavailable");
        when(valueOperations.get(redisKey(KEY))).thenThrow(redisFailure);

        assertThatThrownBy(() -> service.fail(KEY, REQUEST_HASH, "failure"))
                .isSameAs(redisFailure);
    }

    @Test
    void fail_propagatesSerializationFailureWithoutWrappingIt() throws Exception {
        JsonProcessingException jsonFailure = new JsonProcessingException("cannot serialize failure") { };
        ObjectMapper failingMapper = mapperThatFailsToWrite(jsonFailure);
        IdempotencyService failingService = newService(failingMapper);
        when(valueOperations.get(redisKey(KEY))).thenReturn(null);

        Throwable thrown = catchThrowable(() -> failingService.fail(KEY, REQUEST_HASH, "failure"));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to serialize idempotency record");
        assertThat(thrown.getCause()).isSameAs(jsonFailure);
    }

    @Test
    void fail_propagatesRedisWriteFailureWithoutWrappingIt() {
        IllegalStateException redisFailure = new IllegalStateException("write unavailable");
        when(valueOperations.get(redisKey(KEY))).thenReturn(null);
        doThrow(redisFailure).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        assertThatThrownBy(() -> service.fail(KEY, REQUEST_HASH, "failure"))
                .isSameAs(redisFailure);
    }

    private static ObjectMapper mapperThatFailsToWrite(JsonProcessingException failure) {
        return new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw failure;
            }
        };
    }

    private IdempotencyService newService(ObjectMapper mapper) {
        IdempotencyService candidate = new IdempotencyService(redisTemplate, mapper);
        ReflectionTestUtils.setField(candidate, "ttlSeconds", NORMAL_TTL.toSeconds());
        return candidate;
    }

    private void stubOccupied(IdempotencyRecord record) throws Exception {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(valueOperations.get(redisKey(KEY))).thenReturn(json(record));
    }

    private IdempotencyRecord completedRecord(TicketResponse response) {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        return new IdempotencyRecord(REQUEST_HASH, IdempotencyStatus.COMPLETED, response, null, timestamp, timestamp);
    }

    private IdempotencyRecord capturedSetRecord(Duration expectedTtl) throws Exception {
        ArgumentCaptor<String> serializedRecord = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq(redisKey(KEY)), serializedRecord.capture(),
                org.mockito.ArgumentMatchers.eq(expectedTtl));
        return objectMapper.readValue(serializedRecord.getValue(), IdempotencyRecord.class);
    }

    private TicketResponse response(String ticketId) {
        return new TicketResponse(ticketId, "Subject", "Description", "ana@example.com",
                Instant.parse("2026-01-02T03:04:05Z"));
    }

    private String json(IdempotencyRecord record) throws Exception {
        return objectMapper.writeValueAsString(record);
    }

    private String redisKey(String idempotencyKey) {
        return REDIS_PREFIX + sha256(idempotencyKey);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError("SHA-256 must be available in the test JVM", exception);
        }
    }

    private static Stream<CreateTicketRequest> changedRequests() {
        return Stream.of(
                new CreateTicketRequest("Different subject", "Reset link returns an error", "ana@example.com"),
                new CreateTicketRequest("Cannot sign in", "Different description", "ana@example.com"),
                new CreateTicketRequest("Cannot sign in", "Reset link returns an error", "other@example.com"));
    }

    private static Stream<Boolean> occupiedClaimResults() {
        return Stream.of(Boolean.FALSE, null);
    }
}