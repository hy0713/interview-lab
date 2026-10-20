package com.example.interview.security;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenStore {
  private final StringRedisTemplate redis;

  public boolean revoked(String id) {
    return Boolean.TRUE.equals(redis.hasKey("interview:revoked:" + id));
  }

  public void revoke(String id, Duration ttl) {
    if (!ttl.isNegative() && !ttl.isZero())
      redis.opsForValue().set("interview:revoked:" + id, "1", ttl);
  }
}
