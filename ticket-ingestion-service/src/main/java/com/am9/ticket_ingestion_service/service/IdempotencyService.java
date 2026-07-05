package com.am9.ticket_ingestion_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:";
    private final StringRedisTemplate redisTemplate;

    @Value("${app.idempotency.ttl-seconds}")
    private long ttlSeconds;

    public boolean claim(String idempotencyKey, String value){
        String redisKey = KEY_PREFIX + idempotencyKey;
        Boolean wasSet = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, value, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(wasSet);
    }

    public void store(String idempotencyKey, String responseJson){
        String redisKey = KEY_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(redisKey, responseJson, Duration.ofSeconds(ttlSeconds));
    }

    public Optional<String> get(String IdempotencyKey){
        String redisKey = KEY_PREFIX + IdempotencyKey;
        return Optional.ofNullable(redisTemplate.opsForValue().get(redisTemplate));
    }

    public void release(String idempotencyKey) {
        redisTemplate.delete(KEY_PREFIX + idempotencyKey);
    }
}
