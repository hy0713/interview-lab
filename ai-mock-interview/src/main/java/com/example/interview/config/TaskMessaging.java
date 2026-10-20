package com.example.interview.config;

import com.example.interview.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskMessaging {
  private final RabbitTemplate rabbit;
  private final TaskService tasks;

  @TransactionalEventListener
  public void afterCommit(TaskService.TaskReady event) {
    publish(event.id());
  }

  public void publish(Long id) {
    try {
      rabbit.convertAndSend(RabbitConfig.TASK_QUEUE, id.toString());
    } catch (AmqpException e) {
      log.warn("消息暂未发出，PENDING 任务将定时补发: taskId={}", id);
    }
  }

  @RabbitListener(queues = RabbitConfig.TASK_QUEUE)
  public void consume(String taskId) {
    tasks.execute(Long.valueOf(taskId));
  }
}
