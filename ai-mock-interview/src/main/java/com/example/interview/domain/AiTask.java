package com.example.interview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("mi_ai_task")
public class AiTask {
  private Long id;
  private String type;
  private Long targetId;
  private String status;
  private Integer attempts;
  private String errorMessage;
  private LocalDateTime updatedAt;
}
