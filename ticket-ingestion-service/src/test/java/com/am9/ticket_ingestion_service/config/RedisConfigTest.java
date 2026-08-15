package com.am9.ticket_ingestion_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    @Test
    void stringRedisTemplate_usesTheSuppliedConnectionFactory() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        StringRedisTemplate template = new RedisConfig().stringRedisTemplate(connectionFactory);

        assertThat(template).isNotNull();
        assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
    }
}