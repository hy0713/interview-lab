package com.example.interview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("mi_interview")
public class Interview {
  private Long id;
  private Long candidateId;
  private Long interviewerId;
  private String position;
  private String jobDescription;
  private String difficulty;
  private String source;
  private String mode;
  private String phase;
  private Integer maxFollowUps;
  private Integer secondsPerQuestion;
  private String status;
  private String reportStatus;
  private String reportJson;
  private Integer totalScore;
  private LocalDateTime createdAt;
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;
}
