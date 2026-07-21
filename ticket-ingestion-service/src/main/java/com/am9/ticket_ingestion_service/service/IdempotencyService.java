package com.am9.ticket_ingestion_service.service;

import com.am9.ticket_ingestion_service.dto.CreateTicketRequest;
import com.am9.ticket_ingestion_service.dto.IdempotencyDecision;
import com.am9.ticket_ingestion_service.dto.IdempotencyRecord;
import com.am9.ticket_ingestion_service.dto.TicketResponse;
import com.am9.ticket_ingestion_service.exception.DuplicateInFlightException;
import com.am9.ticket_ingestion_service.exception.IdempotencyKeyConflictException;
import com.am9.ticket_ingestion_service.service.enums.IdempotencyStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:create-ticket";
    private static final int MAX_KEY_LENGTH = 128;
    private static final long FAILED_RECORD_TTL_SECONDS = 30;
    @Value("${app.idempotency.ttl-seconds}")
    private long ttlSeconds;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public String fingerprint(CreateTicketRequest request){
        try{
            String canonicalRequest = "POST /api/v1/tickets\n"
                    + objectMapper.writeValueAsString(request);
            return sha256(canonicalRequest);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to create idempotency request fingerprint", ex);        }
    }

    public IdempotencyDecision startOrReturnCompleted(String idempotencyKey, String requestHash){
        validateKey(idempotencyKey);

        String redisKey = redisKey(idempotencyKey);
        Instant now = Instant.now();
        IdempotencyRecord processingRecord = IdempotencyRecord.processing(requestHash, now);

        Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(
                redisKey,
                toJson(processingRecord),
                Duration.ofSeconds(ttlSeconds)
        );

        if(Boolean.TRUE.equals(wasSet)){
            return IdempotencyDecision.processNewRequest();
        }

        String existingJson = redisTemplate.opsForValue().get(redisKey);
        if (existingJson == null){
            return startOrReturnCompleted(idempotencyKey, requestHash);
        }

        IdempotencyRecord existing = fromJson(existingJson);

        if(!existing.requestHash().equals(requestHash)){
            throw new IdempotencyKeyConflictException();
        }

        if (existing.status() == IdempotencyStatus.COMPLETED){
            return IdempotencyDecision.returnCachedResponse(existing.response());
        }

        if (existing.status() == IdempotencyStatus.FAILED){
            return IdempotencyDecision.processNewRequest();
        }

        throw new DuplicateInFlightException(idempotencyKey);
    }

    public void complete(String idempotencyKey, String requestHash, TicketResponse response){
        validateKey(idempotencyKey);
        String redisKey = redisKey(idempotencyKey);

        try {
            String existingJson = redisTemplate.opsForValue().get(redisKey);

            IdempotencyRecord completed;
            if (existingJson == null) {
                Instant now = Instant.now();
                completed = IdempotencyRecord.processing(requestHash, now).completed(response, now);
            }else {
                IdempotencyRecord existing = fromJson(existingJson);
                if (!existing.requestHash().equals(requestHash)) {
                    throw new IdempotencyKeyConflictException();
                }
                completed = existing.completed(response, Instant.now());
            }
            redisTemplate.opsForValue().set(redisKey, toJson(completed), Duration.ofSeconds(ttlSeconds));
        }
        catch (IdempotencyKeyConflictException ex){
            throw ex;
        }catch (Exception ex){
            log.error("Failed to mark idempotency record COMPLETED for key={} after successful publish. "
                            + "Ticket was created but the idempotency cache may be stale until TTL expiry.",
                    idempotencyKey, ex);
            throw new IllegalStateException("Failed to persist completed idempotency record", ex);

        }
    }

    public void fail(String idempotencyKey, String requestHash, String errorMessage) {
        validateKey(idempotencyKey);
        String redisKey = redisKey(idempotencyKey);
        Instant now = Instant.now();
        String existingJson = redisTemplate.opsForValue().get(redisKey);
        IdempotencyRecord base = existingJson == null
                ? IdempotencyRecord.processing(requestHash, now)
                : fromJson(existingJson);
        IdempotencyRecord failedRecord = base.failed(errorMessage, now);
        redisTemplate.opsForValue().set(redisKey, toJson(failedRecord), Duration.ofSeconds(FAILED_RECORD_TTL_SECONDS));
    }

    private String redisKey(String idempotencyKey) {
        return KEY_PREFIX + sha256(idempotencyKey);
    }

    private void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()){
            throw new IllegalArgumentException("Idempotency-Key must not be blank");
        }
        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key must be 128 characters or fewer");
        }
    }

    private String toJson (IdempotencyRecord record){
        try{
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize idempotency record", ex);        }
    }

    private IdempotencyRecord fromJson(String json) {
        try {
            return objectMapper.readValue(json, IdempotencyRecord.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize idempotency record", ex);
        }
    }

    private String sha256(String value) {
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);        }
    }
}
