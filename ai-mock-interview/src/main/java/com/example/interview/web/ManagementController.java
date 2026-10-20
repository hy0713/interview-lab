package com.example.interview.web;

import com.example.interview.common.ApiResult;
import com.example.interview.service.ReviewService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management")
@PreAuthorize("hasRole('INTERVIEWER')")
@RequiredArgsConstructor
@Validated
public class ManagementController {
  private final ReviewService service;

  @GetMapping("/candidates")
  public ApiResult<?> candidates(
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return ApiResult.ok(service.candidates(keyword, page, size));
  }

  @GetMapping("/workbench")
  public ApiResult<?> workbench(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Long candidateId,
      @RequestParam(required = false) @Min(0) @Max(100) Integer minScore) {
    return ApiResult.ok(service.workbench(page, size, keyword, status, candidateId, minScore));
  }
}
