package com.macro.mall.portal.assistant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 本地 H5 调试时，允许页面访问独立客服接口。
 */
@Configuration
@Profile("assistant")
public class AssistantWebConfiguration {

    /** 受信任的本地 H5 页面来源，供 CORS 配置与 {@link AssistantRequestFilter} 共用。 */
    public static final String[] LOCAL_ORIGINS = {
            "http://localhost:5173", "http://127.0.0.1:5173"
    };

    @Bean
    public WebMvcConfigurer assistantCorsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/assistant/**")
                        .allowedOrigins(LOCAL_ORIGINS)
                        .allowedMethods("POST", "OPTIONS")
                        .allowedHeaders("*")
                        .maxAge(3600);
            }
        };
    }
}
