package com.macro.mall.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class LocalRedisHealthConfigTests {
    @Test
    void pingSuccessIsHealthyAndClosesConnectionWithoutParsingInfo() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        assertEquals(Status.UP, new LocalRedisHealthConfig().redisHealthIndicator(factory).health().getStatus());
        verify(connection).ping();
        verify(connection).close();
        verifyNoMoreInteractions(connection);
    }

    @Test
    void failedConnectionOrUnexpectedReplyRemainsUnhealthy() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(new IllegalStateException("unavailable"));
        assertEquals(Status.DOWN, new LocalRedisHealthConfig().redisHealthIndicator(factory).health().getStatus());
        RedisConnection connection = mock(RedisConnection.class);
        reset(factory);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn(null);
        assertEquals(Status.DOWN, new LocalRedisHealthConfig().redisHealthIndicator(factory).health().getStatus());
        verify(connection).close();
    }
}
