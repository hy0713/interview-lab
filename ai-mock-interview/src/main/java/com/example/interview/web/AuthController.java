package com.example.interview.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.common.*;
import com.example.interview.domain.User;
import com.example.interview.mapper.UserMapper;
import com.example.interview.security.*;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final UserMapper users;
  private final PasswordEncoder passwords;
  private final JwtTokens tokens;
  private final TokenStore tokenStore;

  @Value("${app.invite-code}")
  private String inviteCode;

  @PostMapping("/register")
  public ApiResult<?> register(@Valid @RequestBody Requests.Register input) {
    if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new BusinessException("密码的 UTF-8 编码不能超过 72 字节");
    if ("INTERVIEWER".equals(input.role()) && !inviteCode.equals(input.inviteCode()))
      throw new BusinessException("面试官邀请码错误");
    var user = new User();
    user.setUsername(input.username());
    user.setPassword(passwords.encode(input.password()));
    user.setRole(input.role());
    users.insert(user);
    return loginResult(user);
  }

  @PostMapping("/login")
  public ApiResult<?> login(@Valid @RequestBody Requests.Login input) {
    var user =
        users.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, input.username()));
    if (user == null || !passwords.matches(input.password(), user.getPassword()))
      throw new BusinessException(401, "用户名或密码错误");
    return loginResult(user);
  }

  @GetMapping("/me")
  public ApiResult<?> me() {
    return ApiResult.ok(CurrentUser.get());
  }

  @PostMapping("/logout")
  public ApiResult<?> logout(@RequestHeader("Authorization") String authorization) {
    var claims = tokens.parse(authorization.substring(7));
    tokenStore.revoke(
        claims.getId(), Duration.between(Instant.now(), claims.getExpiration().toInstant()));
    return ApiResult.ok(null);
  }

  private ApiResult<?> loginResult(User user) {
    var current = new CurrentUser(user.getId(), user.getUsername(), user.getRole());
    return ApiResult.ok(Map.of("token", tokens.issue(current), "user", current));
  }
}
