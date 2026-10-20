package com.example.interview.web;

import com.example.interview.common.ApiResult;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class HealthController {
  @GetMapping("/api/health")
  public ApiResult<?> health() {
    return ApiResult.ok(Map.of("status", "UP", "application", "Interview Lab"));
  }
}
