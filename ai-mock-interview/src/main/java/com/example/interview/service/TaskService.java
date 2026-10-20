package com.example.interview.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.ai.*;
import com.example.interview.common.Jsons;
import com.example.interview.domain.*;
import com.example.interview.mapper.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {
  public record TaskReady(Long id) {}

  private record ScoreInput(
      Interview interview, Turn turn, List<Turn> history, boolean mayFollowUp) {}

  private final AiTaskMapper tasks;
  private final TurnMapper turns;
  private final InterviewMapper interviews;
  private final InterviewFlow flow;
  private final AiService ai;
  private final Jsons json;
  private final TransactionTemplate tx;
  private final ApplicationEventPublisher events;

  public void create(String type, Long targetId) {
    var task = new AiTask();
    task.setType(type);
    task.setTargetId(targetId);
    task.setStatus("PENDING");
    task.setAttempts(0);
    task.setUpdatedAt(LocalDateTime.now());
    tasks.insert(task);
    events.publishEvent(new TaskReady(task.getId()));
  }

  public void retry(String type, Long targetId) {
    var task =
        tasks.selectOne(
            Wrappers.<AiTask>lambdaQuery()
                .eq(AiTask::getType, type)
                .eq(AiTask::getTargetId, targetId));
    if (task == null || !"FAILED".equals(task.getStatus())) return;
    task.setStatus("PENDING");
    task.setUpdatedAt(LocalDateTime.now());
    task.setErrorMessage("");
    tasks.updateById(task);
    events.publishEvent(new TaskReady(task.getId()));
  }

  public void queueReport(Long interviewId) {
    var interview = interviews.selectById(interviewId);
    if (!"EVALUATING".equals(interview.getStatus())
        || !"WAITING".equals(interview.getReportStatus())) return;
    long unfinished =
        turns.selectCount(
            Wrappers.<Turn>lambdaQuery()
                .eq(Turn::getInterviewId, interviewId)
                .ne(Turn::getScoreStatus, "SUCCEEDED"));
    if (unfinished != 0) return;
    interview.setReportStatus("PENDING");
    interviews.updateById(interview);
    create("REPORT", interviewId);
  }

  public void execute(Long id) {
    var claimed =
        tx.execute(
            status -> {
              int changed =
                  tasks.update(
                      null,
                      Wrappers.<AiTask>lambdaUpdate()
                          .eq(AiTask::getId, id)
                          .eq(AiTask::getStatus, "PENDING")
                          .set(AiTask::getStatus, "RUNNING")
                          .set(AiTask::getUpdatedAt, LocalDateTime.now())
                          .setSql("attempts = attempts + 1"));
              return changed == 0 ? null : tasks.selectById(id);
            });
    if (claimed == null) return;
    try {
      if ("SCORE".equals(claimed.getType())) score(claimed);
      else report(claimed);
    } catch (Exception e) {
      log.error(
          "评估任务失败: taskId={}, type={}, targetId={}",
          id,
          claimed.getType(),
          claimed.getTargetId(),
          e);
      fail(claimed, AiFailures.userMessage(e));
    }
  }

  private void score(AiTask task) {
    ScoreInput input =
        tx.execute(
            status -> {
              var turn = turns.selectById(task.getTargetId());
              var interview = interviews.lock(turn.getInterviewId());
              if (!owned(task)) return null;
              if ("CANCELLED".equals(interview.getStatus())) {
                turn.setScoreStatus("CANCELLED");
                turns.updateById(turn);
                succeed(task);
                return null;
              }
              turn.setScoreStatus("PROCESSING");
              turns.updateById(turn);
              var previous =
                  flow.ordered(interview.getId()).stream()
                      .filter(t -> t.getAnswer() != null)
                      .filter(
                          t ->
                              t.getRootSequence() < turn.getRootSequence()
                                  || (t.getRootSequence().equals(turn.getRootSequence())
                                      && t.getDepth() < turn.getDepth()))
                      .toList();
              return new ScoreInput(interview, turn, previous, flow.mayFollowUp(interview, turn));
            });
    if (input == null) return;
    var assessment =
        ai.assess(input.interview(), input.turn(), input.history(), input.mayFollowUp());
    tx.executeWithoutResult(
        status -> {
          var interview = interviews.lock(input.interview().getId());
          if (!owned(task)) return;
          var turn = turns.selectById(task.getTargetId());
          if ("CANCELLED".equals(interview.getStatus())) {
            turn.setScoreStatus("CANCELLED");
            turns.updateById(turn);
            succeed(task);
            return;
          }
          turn.setScore(ScoreRubrics.score(turn.getCategory(), assessment.evaluation()));
          turn.setEvaluationJson(json.write(assessment.evaluation()));
          turn.setScoreStatus("SUCCEEDED");
          turns.updateById(turn);
          flow.addFollowUp(interview, turn, assessment);
          succeed(task);
          flow.settle(interview);
          queueReport(interview.getId());
        });
  }

  private void report(AiTask task) {
    var interview = interviews.selectById(task.getTargetId());
    var evidence = flow.ordered(interview.getId());
    tx.executeWithoutResult(
        status -> {
          interviews.lock(interview.getId());
          if (owned(task)) {
            interview.setReportStatus("PROCESSING");
            interviews.updateById(interview);
          }
        });
    var result = ai.report(interview.getPosition(), evidence);
    tx.executeWithoutResult(
        status -> {
          interviews.lock(interview.getId());
          if (!owned(task)) return;
          interview.setTotalScore(flow.totalScore(evidence));
          interview.setReportJson(json.write(result));
          interview.setReportStatus("SUCCEEDED");
          interview.setStatus("COMPLETED");
          interviews.updateById(interview);
          succeed(task);
        });
  }

  private boolean owned(AiTask claimed) {
    var current = tasks.selectById(claimed.getId());
    return "RUNNING".equals(current.getStatus())
        && current.getAttempts().equals(claimed.getAttempts());
  }

  private void succeed(AiTask task) {
    task.setStatus("SUCCEEDED");
    task.setUpdatedAt(LocalDateTime.now());
    task.setErrorMessage("");
    tasks.updateById(task);
  }

  public void fail(AiTask task, String message) {
    tx.executeWithoutResult(
        status -> {
          Long interviewId =
              "SCORE".equals(task.getType())
                  ? turns.selectById(task.getTargetId()).getInterviewId()
                  : task.getTargetId();
          var interview = interviews.lock(interviewId);
          if (!owned(task)) return;
          task.setStatus("FAILED");
          task.setErrorMessage(message);
          task.setUpdatedAt(LocalDateTime.now());
          tasks.updateById(task);
          if ("SCORE".equals(task.getType())) {
            var turn = turns.selectById(task.getTargetId());
            turn.setScoreStatus("FAILED");
            turns.updateById(turn);
            flow.settle(interview);
          } else {
            interview.setReportStatus("FAILED");
            interviews.updateById(interview);
          }
        });
  }

  public List<Map<String, Object>> failures(Long interviewId) {
    var targets = flow.ordered(interviewId).stream().map(Turn::getId).toList();
    return tasks
        .selectList(
            Wrappers.<AiTask>lambdaQuery()
                .eq(AiTask::getStatus, "FAILED")
                .and(
                    w ->
                        w.eq(AiTask::getType, "REPORT")
                            .eq(AiTask::getTargetId, interviewId)
                            .or(
                                x ->
                                    x.eq(AiTask::getType, "SCORE")
                                        .in(AiTask::getTargetId, targets))))
        .stream()
        .map(
            t ->
                Map.<String, Object>of(
                    "type",
                    t.getType(),
                    "targetId",
                    t.getTargetId(),
                    "message",
                    t.getErrorMessage()))
        .toList();
  }

  public List<AiTask> pending() {
    return tasks.selectList(
        Wrappers.<AiTask>lambdaQuery()
            .eq(AiTask::getStatus, "PENDING")
            .orderByAsc(AiTask::getId)
            .last("LIMIT 100"));
  }

  public void failStalled() {
    tasks
        .selectList(
            Wrappers.<AiTask>lambdaQuery()
                .eq(AiTask::getStatus, "RUNNING")
                .lt(AiTask::getUpdatedAt, LocalDateTime.now().minusMinutes(10)))
        .forEach(t -> fail(t, "处理超时或进程中断，请重试"));
  }
}
