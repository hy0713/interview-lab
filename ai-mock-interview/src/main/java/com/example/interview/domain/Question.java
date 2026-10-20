package com.example.interview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("mi_question")
public class Question {
  private Long id;
  private String position;
  private String content;
  private String category;
  private String difficulty;
  private String referenceAnswer;
  private String jobFamily;
  private String tags;
  private String reviewStatus;
  private String reviewNote;
  private Long reviewedBy;
  private LocalDateTime reviewedAt;

  @com.baomidou.mybatisplus.annotation.TableField(exist = false)
  private Boolean favorite;

  private LocalDateTime createdAt;
}
