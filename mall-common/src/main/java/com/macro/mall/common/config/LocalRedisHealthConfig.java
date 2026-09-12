package com.macro.mall.common.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** 本地 Windows Redis 的 INFO 含反斜杠路径，使用 PING 检查实际连接，避免属性解析误报。 */
@Configuration
@Profile("dev")
public class LocalRedisHealthConfig {
    @Bean
    public HealthIndicator redisHealthIndicator(RedisConnectionFactory factory) {
        return () -> {
            try (RedisConnection connection = factory.getConnection()) {
                return "PONG".equals(connection.ping())
                        ? Health.up().build()
                        : Health.down().withDetail("reason", "Redis did not acknowledge PING").build();
            } catch (Exception ex) {
                return Health.down().withDetail("reason", "Redis connection failed").build();
            }
        };
    }
}
