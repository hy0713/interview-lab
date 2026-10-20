package com.example.interview.common;

public record ApiResult<T>(int code, String message, T data) {
  public static <T> ApiResult<T> ok(T data) {
    return new ApiResult<>(200, "成功", data);
  }

  public static ApiResult<Void> error(int code, String message) {
    return new ApiResult<>(code, message, null);
  }
}
