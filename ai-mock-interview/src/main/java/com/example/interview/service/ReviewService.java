package com.example.interview.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.common.BusinessException;
import com.example.interview.domain.User;
import com.example.interview.mapper.UserMapper;
import com.example.interview.security.CurrentUser;
import com.example.interview.web.Requests;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {
  private final JdbcTemplate db;
  private final UserMapper users;
  private final InterviewService interviews;

  public Page<User> candidates(String keyword, int page, int size) {
    return users.selectPage(
        new Page<>(page, size),
        Wrappers.<User>lambdaQuery()
            .eq(User::getRole, "CANDIDATE")
            .like(keyword != null && !keyword.isBlank(), User::getUsername, keyword)
            .orderByDesc(User::getId));
  }

  public Map<String, Object> workbench(
      int page, int size, String keyword, String status, Long candidateId, Integer minScore) {
    var args = new ArrayList<Object>();
    String where =
        " FROM mi_interview i JOIN mi_user u ON u.id=i.candidate_id LEFT JOIN mi_review r ON"
            + " r.interview_id=i.id WHERE 1=1";
    if (keyword != null && !keyword.isBlank()) {
      where += " AND (u.username LIKE ? OR i.position LIKE ?)";
      args.add("%" + keyword + "%");
      args.add("%" + keyword + "%");
    }
    if (status != null && !status.isBlank()) {
      where += " AND i.status=?";
      args.add(status);
    }
    if (candidateId != null) {
      where += " AND i.candidate_id=?";
      args.add(candidateId);
    }
    if (minScore != null) {
      where += " AND i.total_score>=?";
      args.add(minScore);
    }
    long total = db.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
    args.add(size);
    args.add((page - 1) * size);
    var rows =
        db.queryForList(
            "SELECT"
                + " i.id,i.position,i.difficulty,i.status,i.total_score,i.created_at,i.candidate_id,u.username"
                + " AS candidate_name,r.score AS human_score,r.comment AS human_comment,(SELECT"
                + " COUNT(*) FROM mi_turn t WHERE t.interview_id=i.id AND"
                + " t.score_status='SUCCEEDED') AS scored_count,(SELECT COUNT(*) FROM mi_turn t"
                + " WHERE t.interview_id=i.id) AS turn_count"
                + where
                + " ORDER BY i.id DESC LIMIT ? OFFSET ?",
            args.toArray());
    return Map.of(
        "records",
        rows,
        "total",
        total,
        "pendingQuestions",
        db.queryForObject(
            "SELECT COUNT(*) FROM mi_question WHERE review_status IN ('DRAFT','PENDING')",
            Long.class),
        "awaitingReview",
        db.queryForObject(
            "SELECT COUNT(*) FROM mi_interview i LEFT JOIN mi_review r ON r.interview_id=i.id WHERE"
                + " i.status='COMPLETED' AND r.interview_id IS NULL",
            Long.class));
  }

  public Object review(Long id, CurrentUser user) {
    interviews.require(id, user, false);
    var rows =
        db.queryForList(
            "SELECT r.score,r.comment,r.updated_at,u.username AS reviewer_name FROM mi_review r"
                + " JOIN mi_user u ON u.id=r.reviewer_id WHERE r.interview_id=?",
            id);
    return rows.isEmpty() ? null : rows.get(0);
  }

  @Transactional
  public void save(Long id, Requests.HumanReview input, CurrentUser user) {
    var interview = interviews.require(id, user, false);
    if (!"COMPLETED".equals(interview.getStatus()))
      throw new BusinessException(409, "报告完成后才能添加人工评语");
    db.update(
        "INSERT INTO mi_review(interview_id,reviewer_id,score,comment) VALUES(?,?,?,?) ON DUPLICATE"
            + " KEY UPDATE"
            + " reviewer_id=VALUES(reviewer_id),score=VALUES(score),comment=VALUES(comment),updated_at=CURRENT_TIMESTAMP",
        id,
        user.id(),
        input.score(),
        input.comment());
  }
}
