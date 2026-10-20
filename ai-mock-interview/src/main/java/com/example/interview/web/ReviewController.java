package com.example.interview.web;

import com.example.interview.common.ApiResult;
import com.example.interview.security.CurrentUser;
import com.example.interview.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interviews/{id}/review")
@RequiredArgsConstructor
public class ReviewController {
  private final ReviewService service;

  @GetMapping
  public ApiResult<?> review(@PathVariable Long id) {
    return ApiResult.ok(service.review(id, CurrentUser.get()));
  }

  @PutMapping
  @PreAuthorize("hasRole('INTERVIEWER')")
  public ApiResult<?> save(@PathVariable Long id, @Valid @RequestBody Requests.HumanReview input) {
    service.save(id, input, CurrentUser.get());
    return review(id);
  }
}
