package com.example.interview.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.ai.*;
import com.example.interview.common.*;
import com.example.interview.domain.*;
import com.example.interview.mapper.*;
import com.example.interview.security.CurrentUser;
import com.example.interview.web.Requests;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class InterviewService {
  private final InterviewMapper interviews;
  private final TurnMapper turns;
  private final QuestionMapper questionMapper;
  private final QuestionService questions;
  private final AiService ai;
  private final TaskService taskService;
  private final DeadlineStore deadlines;
  private final InterviewFlow flow;
  private final TransactionTemplate tx;
  private final Jsons json;
  private final UserMapper users;

  private record Selected(
      String content,
      String category,
      String referenceAnswer,
      Long questionId,
      String tags,
      Long retryOfTurnId) {}

  public Long create(Requests.CreateInterview input, CurrentUser user) {
    Long candidateId = input.candidateId() == null ? user.id() : input.candidateId();
    if (!user.id().equals(candidateId) && !user.interviewer())
      throw new BusinessException(403, "不能为其他人创建面试");
    if (!user.id().equals(candidateId)) {
      var candidate = users.selectById(candidateId);
      if (candidate == null || !"CANDIDATE".equals(candidate.getRole()))
        throw new BusinessException(400, "请选择有效候选人");
    }
    var drafts =
        "AI".equals(input.source())
            ? ai
                .questions(
                    input.position(), input.jobDescription(), input.difficulty(), input.count())
                .stream()
                .map(
                    q ->
                        new Selected(
                            q.content(), q.category(), q.referenceAnswer(), null, ",未分类,", null))
                .toList()
            : questions
                .pick(
                    input.position(),
                    input.difficulty(),
                    input.count(),
                    input.jobFamily(),
                    input.tag())
                .stream()
                .map(
                    q ->
                        new Selected(
                            q.getContent(),
                            q.getCategory(),
                            q.getReferenceAnswer(),
                            q.getId(),
                            q.getTags(),
                            null))
                .toList();
    return createSnapshots(input, user, candidateId, drafts);
  }

  public Long practice(Long turnId, CurrentUser user) {
    var turn = turns.selectById(turnId);
    if (turn == null) throw new BusinessException(404, "回答不存在");
    var original = require(turn.getInterviewId(), user, false);
    if (!user.id().equals(original.getCandidateId())) throw new BusinessException(403, "只能复练自己的回答");
    if (!"COMPLETED".equals(original.getStatus())) throw new BusinessException(409, "请在报告完成后复练");
    var input =
        new Requests.CreateInterview(
            original.getPosition(),
            original.getJobDescription(),
            original.getDifficulty(),
            "BANK",
            1,
            original.getSecondsPerQuestion(),
            "PRACTICE",
            1);
    return createSnapshots(
        input,
        user,
        user.id(),
        List.of(
            new Selected(
                turn.getContent(),
                turn.getCategory(),
                turn.getReferenceAnswer(),
                turn.getQuestionId(),
                turn.getTags(),
                turn.getRetryOfTurnId() == null ? turn.getId() : turn.getRetryOfTurnId())));
  }

  private Long createSnapshots(
      Requests.CreateInterview input, CurrentUser user, Long candidateId, List<Selected> drafts) {
    return tx.execute(
        status -> {
          var interview = new Interview();
          interview.setCandidateId(candidateId);
          interview.setInterviewerId(user.interviewer() ? user.id() : null);
          interview.setPosition(input.position());
          interview.setJobDescription(input.jobDescription());
          interview.setDifficulty(input.difficulty());
          interview.setSource(input.source());
          interview.setSecondsPerQuestion(input.secondsPerQuestion());
          interview.setMode(input.mode() == null ? "PRACTICE" : input.mode());
          interview.setMaxFollowUps(input.maxFollowUps() == null ? 3 : input.maxFollowUps());
          interview.setStatus("CREATED");
          interview.setPhase("READY");
          interview.setReportStatus("NONE");
          interviews.insert(interview);
          int sequence = 0;
          for (var draft : drafts) {
            var turn = new Turn();
            turn.setInterviewId(interview.getId());
            turn.setSequenceNo(++sequence);
            turn.setRootSequence(sequence);
            turn.setDepth(0);
            turn.setContent(draft.content());
            turn.setCategory(draft.category());
            turn.setReferenceAnswer(draft.referenceAnswer());
            turn.setQuestionId(draft.questionId());
            turn.setTags(draft.tags());
            turn.setRetryOfTurnId(draft.retryOfTurnId());
            turn.setStatus("WAITING");
            turn.setScoreStatus("NONE");
            turns.insert(turn);
          }
          return interview.getId();
        });
  }

  public Page<Interview> list(int page, int size, String status, CurrentUser user) {
    return list(page, size, status, null, null, user);
  }

  public Page<Interview> list(
      int page, int size, String status, String keyword, String difficulty, CurrentUser user) {
    return interviews.selectPage(
        new Page<>(page, size),
        Wrappers.<Interview>lambdaQuery()
            .eq(!user.interviewer(), Interview::getCandidateId, user.id())
            .eq(status != null && !status.isBlank(), Interview::getStatus, status)
            .like(keyword != null && !keyword.isBlank(), Interview::getPosition, keyword)
            .eq(difficulty != null && !difficulty.isBlank(), Interview::getDifficulty, difficulty)
            .orderByDesc(Interview::getId));
  }

  public Map<String, Object> stats(CurrentUser user) {
    long total =
        interviews.selectCount(
            Wrappers.<Interview>lambdaQuery()
                .eq(!user.interviewer(), Interview::getCandidateId, user.id()));
    long active =
        interviews.selectCount(
            Wrappers.<Interview>lambdaQuery()
                .eq(!user.interviewer(), Interview::getCandidateId, user.id())
                .in(Interview::getStatus, List.of("IN_PROGRESS", "PAUSED", "EVALUATING")));
    long completed =
        interviews.selectCount(
            Wrappers.<Interview>lambdaQuery()
                .eq(!user.interviewer(), Interview::getCandidateId, user.id())
                .eq(Interview::getStatus, "COMPLETED"));
    return Map.of(
        "total",
        total,
        "active",
        active,
        "completed",
        completed,
        "questionCount",
        questionMapper.selectCount(null),
        "aiConfigured",
        ai.configured());
  }

  public Map<String, Object> detail(Long id, CurrentUser user) {
    var interview = require(id, user, false);
    var report = json.read(interview.getReportJson());
    interview.setReportJson(null);
    var all = flow.ordered(id);
    boolean reveal = Set.of("COMPLETED", "EVALUATING", "CANCELLED").contains(interview.getStatus());
    var result = new LinkedHashMap<String, Object>();
    result.put("interview", interview);
    result.put("serverTime", LocalDateTime.now());
    result.put("report", report);
    result.put("mainQuestionCount", all.stream().filter(t -> t.getDepth() == 0).count());
    result.put("failures", taskService.failures(id));
    result.put("turns", all.stream().map(t -> view(t, reveal, user.interviewer())).toList());
    return result;
  }

  public void start(Long id, CurrentUser user) {
    tx.executeWithoutResult(
        status -> {
          var interview = require(id, user, true);
          if (!"CREATED".equals(interview.getStatus()))
            throw new BusinessException(409, "面试已经开始或结束");
          interview.setStatus("IN_PROGRESS");
          interview.setStartedAt(LocalDateTime.now());
          interviews.updateById(interview);
          flow.activateNext(interview);
        });
  }

  public void next(Long id, CurrentUser user) {
    tx.executeWithoutResult(
        status -> {
          var interview = require(id, user, true);
          if (!"IN_PROGRESS".equals(interview.getStatus()) || !"READY".equals(interview.getPhase()))
            throw new BusinessException(409, "请等待当前回答分析完成后再开始下一题");
          flow.activateNext(interview);
        });
  }

  public void answer(Long id, Long turnId, String answer, CurrentUser user) {
    tx.executeWithoutResult(
        status -> {
          var interview = require(id, user, true);
          if (!"IN_PROGRESS".equals(interview.getStatus()))
            throw new BusinessException(409, "面试当前不可答题");
          var turn = turns.selectById(turnId);
          if (turn == null || !id.equals(turn.getInterviewId()))
            throw new BusinessException(404, "面试题目不存在");
          if (!"ACTIVE".equals(turn.getStatus())) throw new BusinessException(409, "该题已提交或尚未开始");
          if (!turn.getDeadline().isAfter(LocalDateTime.now())) flow.timeout(turn);
          else {
            turn.setAnswer(answer);
            turn.setStatus("ANSWERED");
            turn.setAnsweredAt(LocalDateTime.now());
            turn.setScoreStatus("PENDING");
            turns.updateById(turn);
            taskService.create("SCORE", turnId);
            deadlines.remove(turnId);
          }
          flow.settle(interview);
          taskService.queueReport(id);
        });
  }

  public void pause(Long id, CurrentUser user) {
    tx.executeWithoutResult(
        status -> {
          var interview = require(id, user, true);
          if (!"PRACTICE".equals(interview.getMode()))
            throw new BusinessException(409, "模拟模式的作答计时不可暂停");
          if (!"IN_PROGRESS".equals(interview.getStatus()))
            throw new BusinessException(409, "当前面试不可暂停");
          for (var turn : flow.ordered(id))
            if ("ACTIVE".equals(turn.getStatus())) {
              long remaining = Duration.between(LocalDateTime.now(), turn.getDeadline()).toMillis();
              if (remaining <= 0) throw new BusinessException(409, "本题已经到期，请刷新状态");
              turns.update(
                  null,
                  Wrappers.<Turn>lambdaUpdate()
                      .eq(Turn::getId, turn.getId())
                      .set(Turn::getStatus, "PAUSED")
                      .set(Turn::getRemainingMillis, remaining)
                      .set(Turn::getDeadline, null));
              deadlines.remove(turn.getId());
            }
          interview.setStatus("PAUSED");
          interviews.updateById(interview);
        });
  }

  public void resume(Long id, CurrentUser user) {
    tx.executeWithoutResult(
        status -> {
          var interview = require(id, user, true);
          if (!"PAUSED".equals(interview.getStatus())) throw new BusinessException(409, "面试当前未暂停");
          for (var turn : flow.ordered(id))
            if ("PAUSED".equals(turn.getStatus())) {
              LocalDateTime deadline =
                  LocalDateTime.now().plusNanos(turn.getRemainingMillis() * 1_000_000);
              turns.update(
                  null,
                  Wrappers.<Turn>lambdaUpdate()
                      .eq(Turn::getId, turn.getId())
                      .set(Turn::getStatus, "ACTIVE")
                      .set(Turn::getDeadline, deadline)
                      .set(Turn::getRemainingMillis, null));
              deadlines.add(turn.getId(), deadline);
            }
          interview.setStatus("IN_PROGRESS");
          interviews.updateById(interview);
          flow.settle(interview);
          taskService.queueReport(id);
        });
  }

  public void finish(Long id, CurrentUser user) {
    tx.executeWithoutResult(
        status -> {
          var interview = require(id, user, true);
          if (!Set.of("IN_PROGRESS", "PAUSED").contains(interview.getStatus()))
            throw new BusinessException(409, "当前面试不可交卷");
          skipUnanswered(id, "【提前交卷，未作答】");
          flow.complete(interview);
          taskService.queueReport(id);
        });
  }

  public void cancel(Long id, CurrentUser user) {
    tx.executeWithoutResult(
        status -> {
          var interview = require(id, user, true);
          if (!Set.of("CREATED", "IN_PROGRESS", "PAUSED").contains(interview.getStatus()))
            throw new BusinessException(409, "当前面试不可取消");
          skipUnanswered(id, "【已取消，未作答】");
          interview.setStatus("CANCELLED");
          interview.setPhase("FINISHED");
          interview.setFinishedAt(LocalDateTime.now());
          interviews.updateById(interview);
        });
  }

  public void timeout(Long turnId) {
    tx.executeWithoutResult(
        status -> {
          var before = turns.selectById(turnId);
          if (before == null) {
            deadlines.remove(turnId);
            return;
          }
          var interview = interviews.lock(before.getInterviewId());
          var turn = turns.selectById(turnId);
          if ("IN_PROGRESS".equals(interview.getStatus())
              && "ACTIVE".equals(turn.getStatus())
              && !turn.getDeadline().isAfter(LocalDateTime.now())) {
            flow.timeout(turn);
            flow.settle(interview);
            taskService.queueReport(interview.getId());
          }
          if (!"ACTIVE".equals(turn.getStatus())) deadlines.remove(turnId);
        });
  }

  public void retry(Long id, CurrentUser user) {
    tx.executeWithoutResult(
        status -> {
          var interview = require(id, user, true);
          if (Set.of("CANCELLED", "COMPLETED").contains(interview.getStatus()))
            throw new BusinessException(409, "面试已结束，不能重试");
          for (var turn : flow.ordered(id))
            if ("FAILED".equals(turn.getScoreStatus())) {
              turn.setScoreStatus("PENDING");
              turns.updateById(turn);
              taskService.retry("SCORE", turn.getId());
            }
          if ("FAILED".equals(interview.getReportStatus())) {
            interview.setReportStatus("PENDING");
            interviews.updateById(interview);
            taskService.retry("REPORT", id);
          }
          flow.settle(interview);
          taskService.queueReport(id);
        });
  }

  public Interview require(Long id, CurrentUser user, boolean lock) {
    var interview = lock ? interviews.lock(id) : interviews.selectById(id);
    if (interview == null) throw new BusinessException(404, "面试不存在");
    if (!user.interviewer() && !user.id().equals(interview.getCandidateId()))
      throw new BusinessException(403, "无权访问其他人的面试");
    if (lock && !user.id().equals(interview.getCandidateId()))
      throw new BusinessException(403, "面试作答与进度只能由候选人本人操作");
    return interview;
  }

  private void skipUnanswered(Long id, String reason) {
    for (var turn : flow.ordered(id))
      if (Set.of("WAITING", "ACTIVE", "PAUSED").contains(turn.getStatus())) {
        turn.setStatus("SKIPPED");
        turn.setAnswer(reason);
        turn.setScore(0);
        turn.setScoreStatus("SUCCEEDED");
        turns.updateById(turn);
        deadlines.remove(turn.getId());
      }
  }

  private Map<String, Object> view(Turn turn, boolean reveal, boolean interviewer) {
    var view = new LinkedHashMap<String, Object>();
    view.put("id", turn.getId());
    view.put("sequenceNo", turn.getSequenceNo());
    view.put("rootSequence", turn.getRootSequence());
    view.put("depth", turn.getDepth());
    view.put("parentTurnId", turn.getParentTurnId());
    view.put("questionId", turn.getQuestionId());
    view.put("tags", turn.getTags());
    view.put("retryOfTurnId", turn.getRetryOfTurnId());
    view.put("category", turn.getCategory());
    view.put(
        "followUpReason", "WAITING".equals(turn.getStatus()) ? null : turn.getFollowUpReason());
    view.put(
        "content", !interviewer && "WAITING".equals(turn.getStatus()) ? null : turn.getContent());
    view.put("status", turn.getStatus());
    view.put("scoreStatus", turn.getScoreStatus());
    view.put("answer", turn.getAnswer());
    view.put("deadline", turn.getDeadline());
    view.put("remainingMillis", turn.getRemainingMillis());
    view.put("rubric", ScoreRubrics.forCategory(turn.getCategory()));
    view.put("score", reveal ? turn.getScore() : null);
    view.put("evaluation", reveal ? json.read(turn.getEvaluationJson()) : null);
    view.put("referenceAnswer", reveal ? turn.getReferenceAnswer() : null);
    return view;
  }
}
