package com.example.interview.ai;

import com.example.interview.common.BusinessException;
import java.net.SocketTimeoutException;
import java.util.Locale;

public final class AiFailures {
  private AiFailures() {}

  public static String userMessage(Throwable error) {
    if (error instanceof BusinessException e) return e.getMessage();
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      String message = String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);
      if (message.contains("402") || message.contains("insufficient balance"))
        return "AI 账户余额不足，请联系管理员处理后重试";
      if (message.contains("401")
          || message.contains("unauthorized")
          || message.contains("authentication fails")) return "AI 身份验证失败，请管理员检查 Key";
      if (message.contains("429") || message.contains("rate limit")) return "AI 请求过于频繁，请稍后重试";
      if (cause instanceof SocketTimeoutException || message.contains("timed out"))
        return "AI 响应超时，答案已保留，可以重试";
    }
    return "评估处理失败，请联系管理员查看服务日志";
  }
}
