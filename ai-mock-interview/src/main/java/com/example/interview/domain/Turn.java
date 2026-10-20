package com.example.interview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("mi_turn")
public class Turn {
  private Long id;
  private Long interviewId;
  private Integer sequenceNo;
  private Long parentTurnId;
  private Long questionId;
  private String tags;
  private Long retryOfTurnId;
  private Integer rootSequence;
  private Integer depth;
  private Long remainingMillis;
  private String followUpReason;
  private String content;
  private String category;
  private String referenceAnswer;
  private String answer;
  private String status;
  private String scoreStatus;
  private Integer score;
  private String evaluationJson;
  private LocalDateTime deadline;
  private LocalDateTime answeredAt;
}
