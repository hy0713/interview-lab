package com.example.interview.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Spring AI 1.1 的 DeepSeek 请求类型尚无 thinking 字段，而当前 API 默认启用思考。 显式关闭，避免练习等待过长或推理耗尽最终 JSON 的输出预算。
 * 请求、重试和结果解析仍由 Spring AI 完成。SDK 原生支持后可改为 ChatOptions。 API:
 * https://api-docs.deepseek.com/guides/thinking_mode/
 */
@Component
public class DeepSeekRequestCustomizer implements RestClientCustomizer {
  private final ObjectMapper json;
  private final URI base;

  public DeepSeekRequestCustomizer(
      ObjectMapper json, @Value("${spring.ai.deepseek.base-url}") String baseUrl) {
    this.json = json;
    this.base = URI.create(baseUrl);
  }

  @Override
  public void customize(RestClient.Builder builder) {
    builder.requestInterceptor(
        (request, body, execution) -> {
          var uri = request.getURI();
          if (!base.getAuthority().equals(uri.getAuthority())
              || !uri.getPath().endsWith("/chat/completions"))
            return execution.execute(request, body);
          ObjectNode payload = (ObjectNode) json.readTree(body);
          payload.putObject("thinking").put("type", "disabled");
          byte[] encoded = json.writeValueAsBytes(payload);
          request.getHeaders().setContentLength(encoded.length);
          return execution.execute(request, encoded);
        });
  }
}
