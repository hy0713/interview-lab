package com.example.interview.web;

import jakarta.validation.constraints.*;
import java.util.List;

public final class Requests {
  private Requests() {}

  public record Login(
      @NotBlank(message = "请输入用户名") String username,
      @NotBlank(message = "请输入密码") String password) {}

  public record Register(
      @Pattern(regexp = "[a-zA-Z0-9_]{3,32}", message = "用户名须为 3～32 位字母、数字或下划线") @NotNull
          String username,
      @Size(min = 6, max = 64, message = "密码长度须为 6～64 位") @NotNull String password,
      @Pattern(regexp = "CANDIDATE|INTERVIEWER", message = "角色无效") @NotNull String role,
      String inviteCode) {}

  public record QuestionInput(
      @NotBlank @Size(max = 100) String position,
      @NotBlank @Size(max = 4000) String content,
      @Pattern(regexp = "TECHNICAL|BEHAVIORAL|SCENARIO") @NotNull String category,
      @Pattern(regexp = "EASY|MEDIUM|HARD") @NotNull String difficulty,
      @NotBlank @Size(max = 6000) String referenceAnswer,
      @Pattern(regexp = "JAVA_BACKEND|FRONTEND|TESTING|GENERAL") String jobFamily,
      @Size(max = 8) List<@Pattern(regexp = "[\\p{L}\\p{N}+#. -]{1,30}") String> tags) {}

  public record QuestionReview(
      @NotNull @Pattern(regexp = "APPROVED|REJECTED") String status,
      @NotNull @Size(max = 500) String note) {}

  public record HumanReview(
      @Min(0) @Max(100) Integer score, @NotBlank @Size(max = 2000) String comment) {}

  public record Generate(
      @NotBlank @Size(max = 100) String position,
      @NotBlank @Size(max = 6000) String jobDescription,
      @Pattern(regexp = "EASY|MEDIUM|HARD") @NotNull String difficulty,
      @Min(1) @Max(10) int count) {}

  public record CreateInterview(
      @NotBlank @Size(max = 100) String position,
      @NotNull @Size(max = 6000) String jobDescription,
      @Pattern(regexp = "EASY|MEDIUM|HARD") @NotNull String difficulty,
      @Pattern(regexp = "BANK|AI") @NotNull String source,
      @Min(1) @Max(10) int count,
      @Min(60) @Max(600) int secondsPerQuestion,
      @Pattern(regexp = "PRACTICE|EXAM") String mode,
      @Min(0) @Max(3) Integer maxFollowUps,
      @Positive Long candidateId,
      @Pattern(regexp = "JAVA_BACKEND|FRONTEND|TESTING|GENERAL") String jobFamily,
      @Pattern(regexp = "[\\p{L}\\p{N}+#. -]{1,30}") String tag) {
    public CreateInterview(
        String position,
        String jobDescription,
        String difficulty,
        String source,
        int count,
        int secondsPerQuestion,
        String mode,
        Integer maxFollowUps) {
      this(
          position,
          jobDescription,
          difficulty,
          source,
          count,
          secondsPerQuestion,
          mode,
          maxFollowUps,
          null,
          null,
          null);
    }
  }

  public record Answer(@NotBlank(message = "请填写答案") @Size(max = 12000) String answer) {}
}
