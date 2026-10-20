package com.example.interview.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.ai.AiService;
import com.example.interview.domain.*;
import com.example.interview.mapper.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterviewFlow {
  private final InterviewMapper interviews;
  private final TurnMapper turns;
  private final DeadlineStore deadlines;

  public List<Turn> ordered(Long interviewId) {
    return turns.selectList(
        Wrappers.<Turn>lambdaQuery()
            .eq(Turn::getInterviewId, interviewId)
            .orderByAsc(Turn::getRootSequence)
            .orderByAsc(Turn::getDepth)
            .orderByAsc(Turn::getSequenceNo));
  }

  // 调用者持有面试行锁；分析期间与题目之间不创建 deadline。
  public void activateNext(Interview interview) {
    var next =
        ordered(interview.getId()).stream()
            .filter(t -> "WAITING".equals(t.getStatus()))
            .findFirst();
    if (next.isEmpty()) {
      settle(interview);
      return;
    }
    var turn = next.get();
    turn.setStatus("ACTIVE");
    turn.setDeadline(LocalDateTime.now().plusSeconds(interview.getSecondsPerQuestion()));
    turns.updateById(turn);
    deadlines.add(turn.getId(), turn.getDeadline());
    interview.setPhase("ANSWERING");
    interviews.updateById(interview);
  }

  public void settle(Interview interview) {
    if (!Set.of("IN_PROGRESS", "PAUSED").contains(interview.getStatus())) return;
    var all = ordered(interview.getId());
    if (all.stream().anyMatch(t -> Set.of("ACTIVE", "PAUSED").contains(t.getStatus())))
      interview.setPhase("ANSWERING");
    else if (all.stream().anyMatch(t -> "FAILED".equals(t.getScoreStatus())))
      interview.setPhase("ANALYSIS_FAILED");
    else if (all.stream()
        .anyMatch(t -> Set.of("PENDING", "PROCESSING").contains(t.getScoreStatus())))
      interview.setPhase("ANALYZING");
    else if (all.stream().anyMatch(t -> "WAITING".equals(t.getStatus())))
      interview.setPhase("READY");
    else {
      complete(interview);
      return;
    }
    interviews.updateById(interview);
  }

  public boolean mayFollowUp(Interview interview, Turn turn) {
    if (!Set.of("IN_PROGRESS", "PAUSED").contains(interview.getStatus()) || turn.getDepth() != 0)
      return false;
    var all = ordered(interview.getId());
    return all.stream().filter(t -> t.getParentTurnId() != null).count()
            < interview.getMaxFollowUps()
        && all.stream().noneMatch(t -> turn.getId().equals(t.getParentTurnId()));
  }

  public void addFollowUp(Interview interview, Turn parent, AiService.Assessment assessment) {
    if (!com.example.interview.ai.ScoreRubrics.warrantsFollowUp(parent.getCategory(), assessment)
        || !mayFollowUp(interview, parent)) return;
    var decision = assessment.followUp();
    var child = new Turn();
    child.setInterviewId(interview.getId());
    child.setParentTurnId(parent.getId());
    child.setRootSequence(parent.getRootSequence());
    child.setDepth(1);
    int sequence =
        ordered(interview.getId()).stream().mapToInt(Turn::getSequenceNo).max().orElse(0) + 1;
    child.setSequenceNo(sequence);
    child.setContent(decision.question().content());
    child.setCategory(parent.getCategory());
    child.setReferenceAnswer(decision.question().referenceAnswer());
    child.setTags(parent.getTags());
    child.setFollowUpReason(decision.reason());
    child.setStatus("WAITING");
    child.setScoreStatus("NONE");
    turns.insert(child);
  }

  public void timeout(Turn turn) {
    turn.setStatus("TIMEOUT");
    turn.setAnswer("【超时未答】");
    turn.setAnsweredAt(LocalDateTime.now());
    turn.setScore(0);
    turn.setScoreStatus("SUCCEEDED");
    turns.updateById(turn);
    deadlines.remove(turn.getId());
  }

  public void complete(Interview interview) {
    interview.setStatus("EVALUATING");
    interview.setPhase("FINISHED");
    interview.setReportStatus("WAITING");
    interview.setFinishedAt(LocalDateTime.now());
    interviews.updateById(interview);
  }

  // 先在每道主问题（含追问）内部取平均，再对主问题组取平均，避免追问多的主题权重过大。
  public int totalScore(List<Turn> evidence) {
    var groups = new LinkedHashMap<Integer, List<Integer>>();
    for (var turn : evidence)
      groups.computeIfAbsent(turn.getRootSequence(), key -> new ArrayList<>()).add(turn.getScore());
    return (int)
        Math.round(
            groups.values().stream()
                .mapToDouble(
                    group -> group.stream().mapToInt(Integer::intValue).average().orElse(0))
                .average()
                .orElse(0));
  }
}
