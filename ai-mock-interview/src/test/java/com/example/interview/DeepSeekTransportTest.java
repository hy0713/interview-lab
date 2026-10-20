package com.example.interview;

import static org.assertj.core.api.Assertions.*;

import com.example.interview.ai.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validation;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

class DeepSeekTransportTest {
  @Test
  void realSpringAiClientSendsCompatibleHttpAndParsesStructuredResult() throws Exception {
    var json = new ObjectMapper();
    var requestBody = new AtomicReference<String>();
    var authorization = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/chat/completions",
        exchange -> {
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          String content =
              json.writeValueAsString(
                  Map.of(
                      "questions",
                      List.of(
                          Map.of(
                              "content",
                              "如何避免缓存击穿？",
                              "category",
                              "TECHNICAL",
                              "referenceAnswer",
                              "互斥重建与合理过期策略"))));
          byte[] response =
              json.writeValueAsBytes(
                  Map.of(
                      "id",
                      "test-completion",
                      "object",
                      "chat.completion",
                      "created",
                      1,
                      "model",
                      "test-model",
                      "choices",
                      List.of(
                          Map.of(
                              "index",
                              0,
                              "finish_reason",
                              "stop",
                              "message",
                              Map.of("role", "assistant", "content", content))),
                      "usage",
                      Map.of("prompt_tokens", 10, "completion_tokens", 20, "total_tokens", 30)));
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
      var http = org.springframework.web.client.RestClient.builder();
      new com.example.interview.config.DeepSeekRequestCustomizer(json, baseUrl).customize(http);
      var api =
          DeepSeekApi.builder()
              .baseUrl(baseUrl)
              .restClientBuilder(http)
              .apiKey("local-test-key")
              .build();
      var model =
          DeepSeekChatModel.builder()
              .deepSeekApi(api)
              .defaultOptions(DeepSeekChatOptions.builder().model("test-model").build())
              .build();
      var service =
          new AiService(
              ChatClient.builder(model),
              factory.getValidator(),
              new com.example.interview.common.Jsons(json),
              "local-test-key",
              "test-model");
      assertThat(service.questions("Java 后端", "校招", "MEDIUM", 1).get(0).content()).contains("缓存击穿");
      assertThat(authorization.get()).isEqualTo("Bearer local-test-key");
      var body = json.readTree(requestBody.get());
      assertThat(body.get("model").asText()).isEqualTo("test-model");
      assertThat(body.at("/thinking/type").asText()).isEqualTo("disabled");
      assertThat(body.get("messages").toString()).contains("Java 后端", "questions");
    } finally {
      server.stop(0);
    }
  }
}
