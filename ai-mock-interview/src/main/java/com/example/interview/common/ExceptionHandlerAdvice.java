package com.example.interview.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class ExceptionHandlerAdvice {
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<?> business(BusinessException e) {
    return error(e.getStatus(), e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    return error(400, e.getBindingResult().getFieldErrors().get(0).getDefaultMessage());
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class})
  ResponseEntity<?> invalid(Exception e) {
    return error(400, "请求参数格式错误");
  }

  @ExceptionHandler(DuplicateKeyException.class)
  ResponseEntity<?> duplicate(DuplicateKeyException e) {
    return error(409, "数据已存在，请刷新后重试");
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<?> forbidden(AccessDeniedException e) {
    return error(403, "无权执行此操作");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    log.error("请求处理失败", e);
    return error(500, "服务暂时不可用，请稍后重试");
  }

  private ResponseEntity<?> error(int code, String message) {
    return ResponseEntity.status(code).body(ApiResult.error(code, message));
  }
}
