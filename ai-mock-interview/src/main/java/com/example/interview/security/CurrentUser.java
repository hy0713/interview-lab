package com.example.interview.security;

import org.springframework.security.core.context.SecurityContextHolder;

public record CurrentUser(Long id, String username, String role) {
  public static CurrentUser get() {
    return (CurrentUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }

  public boolean interviewer() {
    return "INTERVIEWER".equals(role);
  }
}
