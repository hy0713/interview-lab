package com.example.interview.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("mi_user")
public class User {
  private Long id;
  private String username;
  @com.fasterxml.jackson.annotation.JsonIgnore private String password;
  private String role;
  private LocalDateTime createdAt;
}
