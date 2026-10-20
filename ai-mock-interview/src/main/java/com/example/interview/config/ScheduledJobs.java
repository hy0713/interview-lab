package com.example.interview.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.domain.Turn;
import com.example.interview.mapper.TurnMapper;
import com.example.interview.service.*;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ScheduledJobs {
  private final TaskService tasks;
  private final TaskMessaging messaging;
  private final DeadlineStore deadlines;
  private final InterviewService interviews;
  private final TurnMapper turns;

  @Scheduled(fixedDelay = 10000, initialDelay = 10000)
  public void dispatch() {
    tasks.pending().forEach(t -> messaging.publish(t.getId()));
    tasks.failStalled();
  }

  @Scheduled(fixedDelay = 5000, initialDelay = 5000)
  public void expire() {
    deadlines.due().forEach(id -> interviews.timeout(Long.valueOf(id)));
  }

  // MySQL 是期限的最终依据，重启或 Redis 丢失索引后最多一分钟补处理。
  @Scheduled(fixedDelay = 60000, initialDelay = 15000)
  public void recoverDeadlines() {
    turns
        .selectList(
            Wrappers.<Turn>lambdaQuery()
                .eq(Turn::getStatus, "ACTIVE")
                .le(Turn::getDeadline, LocalDateTime.now())
                .last("LIMIT 100"))
        .forEach(t -> interviews.timeout(t.getId()));
  }
}
