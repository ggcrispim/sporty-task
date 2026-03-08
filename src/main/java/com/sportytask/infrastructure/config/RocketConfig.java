package com.sportytask.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ Configuration
 *
 * The RocketMQ Spring Boot starter auto-configures the producer and consumer
 * based on application.yaml properties. No manual bean configuration needed.
 */
@Configuration
public class RocketConfig {
    // Auto-configuration from rocketmq-spring-boot-starter handles:
    // - RocketMQTemplate for producing messages
    // - Consumer registration via @RocketMQMessageListener
}
