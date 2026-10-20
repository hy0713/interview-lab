package com.example.interview.service;

import com.example.interview.common.BusinessException;
import com.example.interview.mapper.TurnMapper;
import com.example.interview.security.CurrentUser;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LearningService {
  private final JdbcTemplate db;
  private final TurnMapper turns;
  private final InterviewService interviews;

  public Map<String, Object> mistakes(
      int page, int size, String tag, String keyword, CurrentUser user) {
    var args = new ArrayList<Object>();
    args.add(user.id());
    String where =
        " FROM mi_turn t JOIN mi_interview i ON i.id=t.interview_id WHERE i.candidate_id=? AND"
            + " i.status='COMPLETED' AND t.score_status='SUCCEEDED' AND t.score<75";
    if (tag != null && !tag.isBlank()) {
      where += " AND t.tags LIKE ?";
      args.add("%," + tag + ",%");
    }
    if (keyword != null && !keyword.isBlank()) {
      where += " AND (t.content LIKE ? OR i.position LIKE ?)";
      args.add("%" + keyword + "%");
      args.add("%" + keyword + "%");
    }
    long total = db.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
    args.add(size);
    args.add((page - 1) * size);
    var rows =
        db.queryForList(
            "SELECT"
                + " t.id,t.interview_id,t.content,t.tags,t.score,t.answer,t.category,i.position,i.finished_at"
                + where
                + " ORDER BY i.finished_at DESC,t.id DESC LIMIT ? OFFSET ?",
            args.toArray());
    return Map.of("records", rows, "total", total, "threshold", 75);
  }

  public List<Map<String, Object>> history(Long turnId, CurrentUser user) {
    var turn = turns.selectById(turnId);
    if (turn == null) throw new BusinessException(404, "回答不存在");
    var interview = interviews.require(turn.getInterviewId(), user, false);
    var args = new ArrayList<Object>();
    args.add(interview.getCandidateId());
    String match;
    if (turn.getQuestionId() != null) {
      match = "t.question_id=?";
      args.add(turn.getQuestionId());
    } else {
      Long origin = turn.getRetryOfTurnId() == null ? turn.getId() : turn.getRetryOfTurnId();
      match = "(t.id=? OR t.retry_of_turn_id=?)";
      args.add(origin);
      args.add(origin);
    }
    return db.queryForList(
        "SELECT t.id,t.interview_id,t.answer,t.score,t.content,i.finished_at FROM mi_turn t JOIN"
            + " mi_interview i ON i.id=t.interview_id WHERE i.candidate_id=? AND"
            + " i.status='COMPLETED' AND "
            + match
            + " ORDER BY i.finished_at DESC,t.id DESC LIMIT 20",
        args.toArray());
  }

  public Map<String, Object> overview(String position, String difficulty, CurrentUser user) {
    var args = new ArrayList<Object>();
    args.add(user.id());
    String where = "candidate_id=? AND status='COMPLETED'";
    if (position != null && !position.isBlank()) {
      where += " AND position=?";
      args.add(position);
    }
    if (difficulty != null && !difficulty.isBlank()) {
      where += " AND difficulty=?";
      args.add(difficulty);
    }
    var trend =
        new ArrayList<>(
            db.queryForList(
                "SELECT id,position,difficulty,total_score,finished_at FROM mi_interview WHERE "
                    + where
                    + " ORDER BY finished_at DESC,id DESC LIMIT 30",
                args.toArray()));
    Collections.reverse(trend);
    Map<String, List<Integer>> categories = new TreeMap<>(), tags = new TreeMap<>();
    if (!trend.isEmpty()) {
      var ids = trend.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
      var placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
      for (var row :
          db.queryForList(
              "SELECT category,tags,score FROM mi_turn WHERE score_status='SUCCEEDED' AND"
                  + " interview_id IN ("
                  + placeholders
                  + ")",
              ids.toArray())) {
        int score = ((Number) row.get("score")).intValue();
        categories.computeIfAbsent((String) row.get("category"), k -> new ArrayList<>()).add(score);
        for (var tag : ((String) row.get("tags")).split(","))
          if (!tag.isBlank()) tags.computeIfAbsent(tag, k -> new ArrayList<>()).add(score);
      }
    }
    var abilities =
        categories.entrySet().stream().map(e -> summary(e.getKey(), e.getValue())).toList();
    var weakTags =
        tags.entrySet().stream()
            .map(e -> summary(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingInt(e -> ((Number) e.get("score")).intValue()))
            .toList();
    return Map.of("trend", trend, "abilities", abilities, "weakTags", weakTags, "window", 30);
  }

  private Map<String, Object> summary(String name, List<Integer> scores) {
    return Map.of(
        "name",
        name,
        "score",
        Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0)),
        "samples",
        scores.size());
  }
}
