package com.macro.mall.portal.assistant;

import com.macro.mall.portal.controller.AssistantController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * 仅用于智能客服的轻量服务入口，不依赖商城的数据库和中间件。
 */
@SpringBootApplication(
        scanBasePackages = "com.macro.mall.portal.assistant",
        excludeName = {
                "com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure",
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
                "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
                "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
                "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
        }
)
@Profile("assistant")
@Import(AssistantController.class)
public class AssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssistantApplication.class, args);
    }
}
