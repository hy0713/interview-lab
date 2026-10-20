package com.example.interview.web;

import com.example.interview.ai.*;
import com.example.interview.common.ApiResult;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {
  private final JdbcTemplate database;
  private final StringRedisTemplate redis;
  private final ConnectionFactory rabbit;
  private final AiService ai;

  @GetMapping("/status")
  @PreAuthorize("hasRole('INTERVIEWER')")
  public ApiResult<?> status() {
    var result = new LinkedHashMap<String, Object>();
    result.put("database", check(() -> database.queryForObject("SELECT 1", Integer.class)));
    result.put(
        "redis",
        check(
            () ->
                redis.execute(
                    (org.springframework.data.redis.core.RedisCallback<String>) c -> c.ping())));
    result.put(
        "rabbitmq",
        check(
            () -> {
              try (var connection = rabbit.createConnection()) {
                return connection.isOpen();
              }
            }));
    result.put("aiConfigured", ai.configured());
    result.put("model", ai.model());
    return ApiResult.ok(result);
  }

  @PostMapping("/ai-check")
  @PreAuthorize("hasRole('INTERVIEWER')")
  public ApiResult<?> aiCheck() {
    try {
      ai.probe();
      return ApiResult.ok(Map.of("available", true, "model", ai.model(), "message", "AI 服务可用"));
    } catch (Exception e) {
      log.warn("AI 连通性检查失败", e);
      return ApiResult.ok(
          Map.of("available", false, "model", ai.model(), "message", AiFailures.userMessage(e)));
    }
  }

  private boolean check(java.util.concurrent.Callable<?> action) {
    try {
      Object result = action.call();
      return result != null && !Boolean.FALSE.equals(result);
    } catch (Exception e) {
      log.warn("依赖连通性检查失败: {}", e.getClass().getSimpleName());
      return false;
    }
  }
}
