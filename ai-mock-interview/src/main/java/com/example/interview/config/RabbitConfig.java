package com.example.interview.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
  public static final String TASK_QUEUE = "interview.ai.tasks.v2";

  @Bean
  Queue taskQueue() {
    return new Queue(TASK_QUEUE, true);
  }
}
