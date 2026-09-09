package com.macro.mall.portal.config;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.TimeUnit;

/**
 * 开发环境 MongoDB 可选：本地未启动 MongoDB 时，连接快速失败，不阻塞商城启动。
 * MongoDB 相关收藏、足迹功能在 MongoDB 启动后仍可正常使用。
 */
@Configuration
@Profile("dev")
public class MongoDevConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoDevTimeoutCustomizer() {
        return builder -> builder
                .applyToClusterSettings(settings -> settings.serverSelectionTimeout(1, TimeUnit.SECONDS))
                .applyToSocketSettings(settings -> settings
                        .connectTimeout(1, TimeUnit.SECONDS)
                        .readTimeout(1, TimeUnit.SECONDS));
    }
}
