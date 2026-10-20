package com.example.interview.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeadlineStore {
  private static final String KEY = "interview:deadlines";
  private final StringRedisTemplate redis;

  public void add(Long turnId, LocalDateTime deadline) {
    redis
        .opsForZSet()
        .add(
            KEY,
            turnId.toString(),
            deadline.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
  }

  public void remove(Long turnId) {
    redis.opsForZSet().remove(KEY, turnId.toString());
  }

  public Set<String> due() {
    return redis.opsForZSet().rangeByScore(KEY, 0, System.currentTimeMillis(), 0, 100);
  }
}
