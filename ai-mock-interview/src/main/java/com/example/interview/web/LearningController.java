package com.example.interview.web;

import com.example.interview.common.ApiResult;
import com.example.interview.security.CurrentUser;
import com.example.interview.service.*;
import jakarta.validation.constraints.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
@Validated
public class LearningController {
  private final LearningService learning;
  private final InterviewService interviews;

  @GetMapping("/mistakes")
  public ApiResult<?> mistakes(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String keyword) {
    return ApiResult.ok(learning.mistakes(page, size, tag, keyword, CurrentUser.get()));
  }

  @GetMapping("/overview")
  public ApiResult<?> overview(
      @RequestParam(required = false) String position,
      @RequestParam(required = false) String difficulty) {
    return ApiResult.ok(learning.overview(position, difficulty, CurrentUser.get()));
  }

  @GetMapping("/turns/{id}/history")
  public ApiResult<?> history(@PathVariable Long id) {
    return ApiResult.ok(learning.history(id, CurrentUser.get()));
  }

  @PostMapping("/turns/{id}/practice")
  public ApiResult<?> practice(@PathVariable Long id) {
    return ApiResult.ok(Map.of("id", interviews.practice(id, CurrentUser.get())));
  }
}
