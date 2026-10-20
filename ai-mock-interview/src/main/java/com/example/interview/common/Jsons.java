package com.example.interview.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Jsons {
  private final ObjectMapper mapper;

  public String write(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("无法序列化业务数据", e);
    }
  }

  public Object read(String value) {
    if (value == null) return null;
    try {
      return mapper.readTree(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("数据库 JSON 格式错误", e);
    }
  }
}
