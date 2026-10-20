package com.example.interview.web;

import com.example.interview.common.ApiResult;
import com.example.interview.security.CurrentUser;
import com.example.interview.service.QuestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/questions")
@RequiredArgsConstructor
@Validated
public class QuestionController {
  private final QuestionService service;

  @GetMapping
  public ApiResult<?> list(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String difficulty,
      @RequestParam(required = false) String jobFamily,
      @RequestParam(required = false) String tag,
      @RequestParam(required = false) String reviewStatus,
      @RequestParam(defaultValue = "false") boolean favorites) {
    return ApiResult.ok(
        service.list(
            page,
            size,
            keyword,
            difficulty,
            jobFamily,
            tag,
            reviewStatus,
            favorites,
            CurrentUser.get()));
  }

  @GetMapping("/tags")
  public ApiResult<?> tags() {
    return ApiResult.ok(service.tags(CurrentUser.get()));
  }

  @PutMapping("/{id}/favorite")
  public ApiResult<?> favorite(@PathVariable Long id) {
    service.favorite(id, true, CurrentUser.get());
    return ApiResult.ok(null);
  }

  @DeleteMapping("/{id}/favorite")
  public ApiResult<?> unfavorite(@PathVariable Long id) {
    service.favorite(id, false, CurrentUser.get());
    return ApiResult.ok(null);
  }

  @PostMapping("/{id}/review")
  @PreAuthorize("hasRole('INTERVIEWER')")
  public ApiResult<?> review(
      @PathVariable Long id, @Valid @RequestBody Requests.QuestionReview input) {
    service.review(id, input, CurrentUser.get());
    return ApiResult.ok(null);
  }

  @PostMapping
  @PreAuthorize("hasRole('INTERVIEWER')")
  public ApiResult<?> add(@Valid @RequestBody Requests.QuestionInput input) {
    return ApiResult.ok(service.save(null, input));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('INTERVIEWER')")
  public ApiResult<?> edit(
      @PathVariable Long id, @Valid @RequestBody Requests.QuestionInput input) {
    return ApiResult.ok(service.save(id, input));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('INTERVIEWER')")
  public ApiResult<?> delete(@PathVariable Long id) {
    service.delete(id);
    return ApiResult.ok(null);
  }

  @PostMapping("/generate")
  @PreAuthorize("hasRole('INTERVIEWER')")
  public ApiResult<?> generate(@Valid @RequestBody Requests.Generate input) {
    return ApiResult.ok(service.generate(input));
  }
}
