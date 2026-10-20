package com.example.interview.web;

import com.example.interview.common.ApiResult;
import com.example.interview.security.CurrentUser;
import com.example.interview.service.InterviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
@Validated
public class InterviewController {
  private final InterviewService service;

  @PostMapping
  public ApiResult<?> create(@Valid @RequestBody Requests.CreateInterview input) {
    return ApiResult.ok(Map.of("id", service.create(input, CurrentUser.get())));
  }

  @GetMapping
  public ApiResult<?> list(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String difficulty) {
    var result = service.list(page, size, status, keyword, difficulty, CurrentUser.get());
    // 报告在详情接口按需解析，列表不传完整报告文本。
    result.getRecords().forEach(i -> i.setReportJson(null));
    return ApiResult.ok(result);
  }

  @GetMapping("/stats")
  public ApiResult<?> stats() {
    return ApiResult.ok(service.stats(CurrentUser.get()));
  }

  @GetMapping("/{id}")
  public ApiResult<?> detail(@PathVariable Long id) {
    return ApiResult.ok(service.detail(id, CurrentUser.get()));
  }

  @PostMapping("/{id}/start")
  public ApiResult<?> start(@PathVariable Long id) {
    service.start(id, CurrentUser.get());
    return detail(id);
  }

  @PostMapping("/{id}/turns/{turnId}/answer")
  public ApiResult<?> answer(
      @PathVariable Long id, @PathVariable Long turnId, @Valid @RequestBody Requests.Answer input) {
    service.answer(id, turnId, input.answer(), CurrentUser.get());
    return detail(id);
  }

  @PostMapping("/{id}/finish")
  public ApiResult<?> finish(@PathVariable Long id) {
    service.finish(id, CurrentUser.get());
    return detail(id);
  }

  @PostMapping("/{id}/next")
  public ApiResult<?> next(@PathVariable Long id) {
    service.next(id, CurrentUser.get());
    return detail(id);
  }

  @PostMapping("/{id}/pause")
  public ApiResult<?> pause(@PathVariable Long id) {
    service.pause(id, CurrentUser.get());
    return detail(id);
  }

  @PostMapping("/{id}/resume")
  public ApiResult<?> resume(@PathVariable Long id) {
    service.resume(id, CurrentUser.get());
    return detail(id);
  }

  @PostMapping("/{id}/cancel")
  public ApiResult<?> cancel(@PathVariable Long id) {
    service.cancel(id, CurrentUser.get());
    return detail(id);
  }

  @PostMapping("/{id}/retry")
  public ApiResult<?> retry(@PathVariable Long id) {
    service.retry(id, CurrentUser.get());
    return detail(id);
  }
}
