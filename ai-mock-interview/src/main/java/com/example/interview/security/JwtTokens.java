package com.example.interview.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokens {
  private final SecretKey key;
  private final long hours;

  public JwtTokens(
      @Value("${app.jwt-secret}") String secret, @Value("${app.token-hours}") long hours) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.hours = hours;
  }

  public String issue(CurrentUser user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(user.id().toString())
        .id(UUID.randomUUID().toString())
        .claim("username", user.username())
        .claim("role", user.role())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(hours * 3600)))
        .signWith(key)
        .compact();
  }

  public Claims parse(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }
}
