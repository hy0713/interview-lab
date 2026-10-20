package com.example.interview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.interview.ai.*;
import com.example.interview.domain.*;
import com.example.interview.mapper.*;
import com.example.interview.security.*;
import com.example.interview.service.*;
import com.example.interview.web.Requests;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterviewFlowTest {
  @Autowired InterviewService service;
  @Autowired InterviewFlow flow;
  @Autowired TaskService worker;
  @Autowired UserMapper users;
  @Autowired InterviewMapper interviews;
  @Autowired QuestionMapper questions;
  @Autowired TurnMapper turns;
  @Autowired AiTaskMapper tasks;
  @Autowired MockMvc mvc;
  @Autowired QuestionService bank;
  @Autowired LearningService learning;
  @Autowired ReviewService reviews;
  @Autowired JwtTokens tokens;
  @MockitoBean AiService ai;
  @MockitoBean DeadlineStore deadlines;
  @MockitoBean TokenStore tokenStore;
  @MockitoBean RabbitTemplate rabbit;
  CurrentUser owner, other;

  @BeforeEach
  void setup() {
    tasks.delete(null);
    turns.delete(null);
    interviews.delete(null);
    questions.delete(null);
    users.delete(null);
    owner = user("owner");
    other = user("other");
    for (int i = 0; i < 3; i++) {
      var q = new Question();
      q.setPosition("Java 后端");
      q.setContent("解释 Java 并发原理 " + i);
      q.setCategory("TECHNICAL");
      q.setDifficulty("MEDIUM");
      q.setReferenceAnswer("互斥、可见性、原子性");
      questions.insert(q);
    }
    doAnswer(i -> assessment(i.getArgument(1), 80, false))
        .when(ai)
        .assess(any(), any(), anyList(), anyBoolean());
    when(ai.report(anyString(), anyList()))
        .thenReturn(
            new AiService.Report(
                "整体表现稳定", List.of("基础扎实"), List.of("实践细节较少"), List.of("练习线程安全示例")));
  }

  CurrentUser user(String name) {
    var u = new User();
    u.setUsername(name);
    u.setPassword("unused");
    u.setRole("CANDIDATE");
    users.insert(u);
    return new CurrentUser(u.getId(), name, "CANDIDATE");
  }

  Long create(int count) {
    return create(count, "PRACTICE", 2);
  }

  Long create(int count, String mode, int max) {
    return service.create(
        new Requests.CreateInterview("Java 后端", "应届生", "MEDIUM", "BANK", count, 60, mode, max),
        owner);
  }

  Turn active(Long id) {
    return turns.selectOne(
        Wrappers.<Turn>lambdaQuery().eq(Turn::getInterviewId, id).eq(Turn::getStatus, "ACTIVE"));
  }

  AiTask task(String type, Long target) {
    return tasks.selectOne(
        Wrappers.<AiTask>lambdaQuery().eq(AiTask::getType, type).eq(AiTask::getTargetId, target));
  }

  void score(Turn turn) {
    worker.execute(task("SCORE", turn.getId()).getId());
  }

  AiService.Assessment assessment(Turn turn, int score, boolean followUp) {
    var marks =
        ScoreRubrics.forCategory(turn.getCategory()).stream()
            .map(r -> new AiService.DimensionMark(r.code(), score, "根据回答评估"))
            .toList();
    var evaluation =
        new AiService.Evaluation(
            marks,
            "原理部分正确",
            List.of(new AiService.Evidence(turn.getAnswer(), "SUPPORTED", "包含相关说明")),
            List.of("基础概念"),
            List.of("实践细节"),
            List.of(),
            List.of("结合实例说明边界"));
    return new AiService.Assessment(
        evaluation,
        new AiService.FollowUp(
            followUp,
            followUp ? "验证你提到的实现细节" : "本轮结束",
            followUp
                ? new AiService.QuestionDraft(
                    "你提到的方案在并发调用时如何保证正确？", turn.getCategory(), "解释共享状态和同步边界")
                : null));
  }

  @Test
  void fullFlowWaitsForScoresAndConfirmationBeforeNextQuestion() {
    Long id = create(2);
    service.start(id, owner);
    Turn first = active(id);
    service.answer(id, first.getId(), "使用锁保护临界区", owner);
    assertThat(active(id)).isNull();
    assertThat(interviews.selectById(id).getPhase()).isEqualTo("ANALYZING");
    assertThatThrownBy(() -> service.next(id, owner)).hasMessageContaining("等待");
    score(first);
    assertThat(interviews.selectById(id).getPhase()).isEqualTo("READY");
    service.next(id, owner);
    Turn second = active(id);
    service.answer(id, second.getId(), "通过 happens-before 保证可见性", owner);
    assertThat(task("REPORT", id)).isNull();
    score(second);
    assertThat(interviews.selectById(id).getStatus()).isEqualTo("EVALUATING");
    worker.execute(task("REPORT", id).getId());
    assertThat(interviews.selectById(id).getStatus()).isEqualTo("COMPLETED");
    assertThat(interviews.selectById(id).getTotalScore()).isEqualTo(80);
  }

  @Test
  void adaptiveFollowUpUsesHistoryAndComesBeforeNextMainQuestion() {
    Long id = create(2);
    service.start(id, owner);
    Turn first = active(id);
    doAnswer(i -> assessment(i.getArgument(1), 80, ((Turn) i.getArgument(1)).getDepth() == 0))
        .when(ai)
        .assess(any(), any(), anyList(), anyBoolean());
    service.answer(id, first.getId(), "我使用过锁", owner);
    score(first);
    var ordered = flow.ordered(id);
    assertThat(ordered).hasSize(3);
    assertThat(ordered.get(1).getParentTurnId()).isEqualTo(first.getId());
    assertThat(ordered.get(1).getDeadline()).isNull();
    assertThat(active(id)).isNull();
    service.next(id, owner);
    Turn child = active(id);
    assertThat(child.getDepth()).isEqualTo(1);
    assertThat(child.getRootSequence()).isEqualTo(1);
    service.answer(id, child.getId(), "锁保护共享变量", owner);
    score(child);
    verify(ai)
        .assess(
            any(),
            argThat(t -> t.getId().equals(child.getId())),
            argThat(history -> history.size() == 1 && history.get(0).getAnswer().equals("我使用过锁")),
            eq(false));
    service.next(id, owner);
    assertThat(active(id).getRootSequence()).isEqualTo(2);
  }

  @Test
  void globalFollowUpLimitIsEnforcedEvenIfModelAsksForMore() {
    Long id = create(2, "PRACTICE", 1);
    service.start(id, owner);
    doAnswer(i -> assessment(i.getArgument(1), 70, true))
        .when(ai)
        .assess(any(), any(), anyList(), anyBoolean());
    Turn first = active(id);
    service.answer(id, first.getId(), "第一题回答", owner);
    score(first);
    service.next(id, owner);
    Turn child = active(id);
    service.answer(id, child.getId(), "追问回答", owner);
    score(child);
    service.next(id, owner);
    Turn second = active(id);
    service.answer(id, second.getId(), "第二题回答", owner);
    score(second);
    assertThat(flow.ordered(id)).hasSize(3);
    assertThat(interviews.selectById(id).getStatus()).isEqualTo("EVALUATING");
  }

  @Test
  void duplicateSubmissionCannotAdvanceTwice() {
    Long id = create(3);
    service.start(id, owner);
    Turn first = active(id);
    service.answer(id, first.getId(), "回答", owner);
    assertThatThrownBy(() -> service.answer(id, first.getId(), "重复回答", owner))
        .hasMessageContaining("已提交");
    assertThat(active(id)).isNull();
    assertThat(tasks.selectCount(null)).isEqualTo(1);
  }

  @Test
  void highScoreWithoutErrorsStopsUnnecessaryFollowUp() {
    Long id = create(1);
    service.start(id, owner);
    Turn first = active(id);
    service.answer(id, first.getId(), "完整回答", owner);
    doAnswer(i -> assessment(i.getArgument(1), 90, true))
        .when(ai)
        .assess(any(), any(), anyList(), anyBoolean());
    score(first);
    assertThat(flow.ordered(id)).hasSize(1);
    assertThat(interviews.selectById(id).getStatus()).isEqualTo("EVALUATING");
    assertThat(task("REPORT", id)).isNotNull();
  }

  @Test
  void timeoutDoesNotStartNextTimerWhileUserIsAway() {
    Long id = create(2);
    service.start(id, owner);
    Turn first = active(id);
    first.setDeadline(LocalDateTime.now().minusSeconds(1));
    turns.updateById(first);
    service.timeout(first.getId());
    service.timeout(first.getId());
    assertThat(turns.selectById(first.getId()).getStatus()).isEqualTo("TIMEOUT");
    assertThat(turns.selectById(first.getId()).getScore()).isZero();
    assertThat(active(id)).isNull();
    assertThat(flow.ordered(id).get(1).getDeadline()).isNull();
    service.next(id, owner);
    assertThat(active(id).getRootSequence()).isEqualTo(2);
  }

  @Test
  void lateAnswerIsRecordedAsTimeoutWithoutCallingAi() {
    Long id = create(1);
    service.start(id, owner);
    Turn first = active(id);
    first.setDeadline(LocalDateTime.now().minusSeconds(1));
    turns.updateById(first);
    service.answer(id, first.getId(), "迟交", owner);
    assertThat(turns.selectById(first.getId()).getStatus()).isEqualTo("TIMEOUT");
    assertThat(task("SCORE", first.getId())).isNull();
    assertThat(task("REPORT", id)).isNotNull();
  }

  @Test
  void practicePausePreservesExactRemainingTimeAndResumeRestoresDeadline() {
    Long id = create(1);
    service.start(id, owner);
    Turn first = active(id);
    first.setDeadline(LocalDateTime.now().plusSeconds(40));
    turns.updateById(first);
    service.pause(id, owner);
    var paused = turns.selectById(first.getId());
    assertThat(paused.getStatus()).isEqualTo("PAUSED");
    assertThat(paused.getDeadline()).isNull();
    assertThat(paused.getRemainingMillis()).isBetween(38000L, 40000L);
    service.timeout(first.getId());
    assertThat(turns.selectById(first.getId()).getStatus()).isEqualTo("PAUSED");
    service.resume(id, owner);
    var resumed = active(id);
    assertThat(resumed.getRemainingMillis()).isNull();
    assertThat(Duration.between(LocalDateTime.now(), resumed.getDeadline()).toMillis())
        .isBetween(paused.getRemainingMillis() - 1500, paused.getRemainingMillis());
  }

  @Test
  void examDoesNotAllowPausingCurrentQuestion() {
    Long id = create(1, "EXAM", 0);
    service.start(id, owner);
    assertThatThrownBy(() -> service.pause(id, owner)).hasMessageContaining("不可暂停");
  }

  @Test
  void analysisCanFinishWhilePausedWithoutStartingFollowUpTimer() {
    Long id = create(1);
    service.start(id, owner);
    Turn first = active(id);
    doAnswer(i -> assessment(i.getArgument(1), 70, true))
        .when(ai)
        .assess(any(), any(), anyList(), anyBoolean());
    service.answer(id, first.getId(), "回答", owner);
    service.pause(id, owner);
    score(first);
    assertThat(interviews.selectById(id).getStatus()).isEqualTo("PAUSED");
    assertThat(flow.ordered(id).get(1).getDeadline()).isNull();
    service.resume(id, owner);
    assertThat(interviews.selectById(id).getPhase()).isEqualTo("READY");
  }

  @Test
  void earlyFinishCountsUnansweredAsZero() {
    Long id = create(2);
    service.start(id, owner);
    Turn first = active(id);
    service.answer(id, first.getId(), "回答", owner);
    service.finish(id, owner);
    score(first);
    worker.execute(task("REPORT", id).getId());
    assertThat(interviews.selectById(id).getTotalScore()).isEqualTo(40);
  }

  @Test
  void finishingWhileAiRunsCannotAppendLateFollowUp() {
    Long id = create(2);
    service.start(id, owner);
    Turn first = active(id);
    service.answer(id, first.getId(), "回答", owner);
    doAnswer(
            i -> {
              service.finish(id, owner);
              return assessment(i.getArgument(1), 80, true);
            })
        .when(ai)
        .assess(any(), any(), anyList(), anyBoolean());
    score(first);
    assertThat(flow.ordered(id)).hasSize(2);
    assertThat(task("REPORT", id)).isNotNull();
  }

  @Test
  void cancelledSessionDoesNotReceiveLateFollowUpOrReport() {
    Long id = create(1);
    service.start(id, owner);
    Turn first = active(id);
    service.answer(id, first.getId(), "回答", owner);
    doAnswer(
            i -> {
              service.cancel(id, owner);
              return assessment(i.getArgument(1), 80, true);
            })
        .when(ai)
        .assess(any(), any(), anyList(), anyBoolean());
    score(first);
    assertThat(interviews.selectById(id).getStatus()).isEqualTo("CANCELLED");
    assertThat(flow.ordered(id)).hasSize(1);
    assertThat(task("REPORT", id)).isNull();
    assertThat(turns.selectById(first.getId()).getScoreStatus()).isEqualTo("CANCELLED");
  }

  @Test
  void scoreFailureIsVisibleAndRetryCanComplete() {
    Long id = create(1);
    service.start(id, owner);
    Turn first = active(id);
    service.answer(id, first.getId(), "回答", owner);
    doThrow(new IllegalStateException("upstream unavailable"))
        .when(ai)
        .assess(any(), any(), anyList(), anyBoolean());
    score(first);
    assertThat(turns.selectById(first.getId()).getScoreStatus()).isEqualTo("FAILED");
    assertThat(interviews.selectById(id).getPhase()).isEqualTo("ANALYSIS_FAILED");
    doAnswer(i -> assessment(i.getArgument(1), 60, false))
        .when(ai)
        .assess(any(), any(), anyList(), anyBoolean());
    service.retry(id, owner);
    score(first);
    assertThat(turns.selectById(first.getId()).getScore()).isEqualTo(60);
    assertThat(task("REPORT", id)).isNotNull();
  }

  @Test
  void duplicateMqDeliveryDoesNotCallAiOrCreateFollowUpTwice() {
    Long id = create(1);
    service.start(id, owner);
    Turn first = active(id);
    service.answer(id, first.getId(), "回答", owner);
    doAnswer(i -> assessment(i.getArgument(1), 80, true))
        .when(ai)
        .assess(any(), any(), anyList(), anyBoolean());
    score(first);
    score(first);
    verify(ai, times(1)).assess(any(), any(), anyList(), anyBoolean());
    assertThat(flow.ordered(id)).hasSize(2);
  }

  @Test
  void rootGroupsHaveEqualWeightEvenWhenOneHasFollowUp() {
    var first = new Turn();
    first.setRootSequence(1);
    first.setScore(40);
    var child = new Turn();
    child.setRootSequence(1);
    child.setScore(80);
    var second = new Turn();
    second.setRootSequence(2);
    second.setScore(100);
    assertThat(flow.totalScore(List.of(first, child, second))).isEqualTo(80);
  }

  @Test
  void anotherCandidateCannotAccessOrMutateInterview() throws Exception {
    Long id = create(1);
    String token = tokens.issue(other);
    mvc.perform(get("/api/interviews/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    for (String action : List.of("start", "next", "pause", "resume", "cancel", "retry"))
      mvc.perform(
              post("/api/interviews/" + id + "/" + action)
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
  }

  @Test
  void candidateCannotModifyQuestionBank() throws Exception {
    mvc.perform(
            delete("/api/questions/" + questions.selectList(null).get(0).getId())
                .header("Authorization", "Bearer " + tokens.issue(owner)))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticatedAndRevokedTokensAreRejected() throws Exception {
    mvc.perform(get("/api/interviews")).andExpect(status().isUnauthorized());
    when(tokenStore.revoked(anyString())).thenReturn(true);
    mvc.perform(get("/api/interviews").header("Authorization", "Bearer " + tokens.issue(owner)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void snapshotsAndEvidenceRemainHiddenUntilInterviewIsFinished() {
    Long id = create(2);
    var q = questions.selectList(null).get(0);
    q.setContent("被修改的题目");
    questions.updateById(q);
    service.start(id, owner);
    Turn first = active(id);
    assertThat(first.getContent()).startsWith("解释 Java");
    service.answer(id, first.getId(), "回答", owner);
    score(first);
    var view = (Map<?, ?>) ((List<?>) service.detail(id, owner).get("turns")).get(0);
    assertThat(view.get("referenceAnswer")).isNull();
    assertThat(view.get("evaluation")).isNull();
    assertThat(view.get("score")).isNull();
  }

  @Test
  void invalidCountIsRejectedAtHttpBoundary() throws Exception {
    mvc.perform(
            post("/api/interviews")
                .header("Authorization", "Bearer " + tokens.issue(owner))
                .contentType("application/json")
                .content(
                    "{\"position\":\"Java\",\"jobDescription\":\"\",\"difficulty\":\"MEDIUM\",\"source\":\"BANK\",\"count\":100,\"secondsPerQuestion\":60}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void concurrentSubmissionsOnlyAcceptOneAnswer() throws Exception {
    Long id = create(2);
    service.start(id, owner);
    Long turnId = active(id).getId();
    var ready = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> attempt =
          () -> {
            ready.await();
            try {
              service.answer(id, turnId, "并发回答", owner);
              return true;
            } catch (com.example.interview.common.BusinessException e) {
              return false;
            }
          };
      var first = pool.submit(attempt);
      var second = pool.submit(attempt);
      ready.countDown();
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(active(id)).isNull();
      assertThat(tasks.selectCount(null)).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void registrationLoginAndLogoutUseRealSecurityFlow() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType("application/json")
                .content(
                    "{\"username\":\"graduate\",\"password\":\"Interview123!\",\"role\":\"CANDIDATE\"}"))
        .andExpect(status().isOk());
    var result =
        mvc.perform(
                post("/api/auth/login")
                    .contentType("application/json")
                    .content("{\"username\":\"graduate\",\"password\":\"Interview123!\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String token =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(result.getResponse().getContentAsString())
            .at("/data/token")
            .asText();
    mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value("graduate"));
    mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    verify(tokenStore).revoke(anyString(), any());
  }

  Long completed(int mark) {
    Long id = create(1);
    service.start(id, owner);
    var turn = active(id);
    doAnswer(i -> assessment(i.getArgument(1), mark, false))
        .when(ai)
        .assess(any(), any(), anyList(), anyBoolean());
    service.answer(id, turn.getId(), "实际回答 " + mark, owner);
    score(turn);
    worker.execute(task("REPORT", id).getId());
    return id;
  }

  @Test
  void questionReviewAndTagSelectionExcludeUnpublishedQuestions() {
    var q =
        bank.save(
            null,
            new Requests.QuestionInput(
                "Spring 专项",
                "解释事务代理",
                "TECHNICAL",
                "MEDIUM",
                "代理边界",
                "JAVA_BACKEND",
                List.of("Spring", "事务")));
    assertThat(bank.list(1, 10, null, null, null, "事务", null, false, owner).getTotal()).isZero();
    bank.review(q.getId(), new Requests.QuestionReview("APPROVED", "通过"), other);
    var selected = bank.pick("任意岗位", "MEDIUM", 1, "JAVA_BACKEND", "事务");
    assertThat(selected).extracting(Question::getId).containsExactly(q.getId());
    var publicRow =
        bank.list(1, 10, null, null, null, "事务", null, false, owner).getRecords().get(0);
    assertThat(publicRow.getReferenceAnswer()).isNull();
    bank.save(
        q.getId(),
        new Requests.QuestionInput(
            "Spring 专项", "修改后的问题", "TECHNICAL", "MEDIUM", "新要点", "JAVA_BACKEND", List.of("事务")));
    assertThatThrownBy(() -> bank.pick("任意岗位", "MEDIUM", 1, "JAVA_BACKEND", "事务"))
        .hasMessageContaining("不足");
  }

  @Test
  void favoritesAreIdempotentAndBelongToCurrentUser() {
    Long q = questions.selectList(null).get(0).getId();
    bank.favorite(q, true, owner);
    bank.favorite(q, true, owner);
    assertThat(bank.list(1, 10, null, null, null, null, null, true, owner).getTotal()).isEqualTo(1);
    assertThat(bank.list(1, 10, null, null, null, null, null, true, other).getTotal()).isZero();
    bank.favorite(q, false, owner);
    assertThat(bank.list(1, 10, null, null, null, null, null, true, owner).getTotal()).isZero();
  }

  @Test
  void interviewerCanAssignButCannotAnswerForCandidate() throws Exception {
    var admin = new CurrentUser(other.id(), "admin", "INTERVIEWER");
    var input =
        new Requests.CreateInterview(
            "Java 后端", "指派", "MEDIUM", "BANK", 1, 60, "PRACTICE", 0, owner.id(), null, null);
    Long id = service.create(input, admin);
    assertThat(interviews.selectById(id).getCandidateId()).isEqualTo(owner.id());
    assertThat(interviews.selectById(id).getInterviewerId()).isEqualTo(admin.id());
    assertThatThrownBy(
            () ->
                service.create(
                    new Requests.CreateInterview(
                        "Java 后端",
                        "",
                        "MEDIUM",
                        "BANK",
                        1,
                        60,
                        "PRACTICE",
                        0,
                        other.id(),
                        null,
                        null),
                    owner))
        .hasMessageContaining("其他人");
    mvc.perform(
            post("/api/interviews/" + id + "/start")
                .header("Authorization", "Bearer " + tokens.issue(admin)))
        .andExpect(status().isForbidden());
    service.start(id, owner);
    assertThat(active(id)).isNotNull();
    mvc.perform(
            get("/api/management/workbench")
                .header("Authorization", "Bearer " + tokens.issue(owner)))
        .andExpect(status().isForbidden());
  }

  @Test
  void practicePreservesSnapshotAndHistoryRemainsPrivate() {
    Long original = completed(40);
    var source = flow.ordered(original).get(0);
    questions.deleteById(source.getQuestionId());
    Long retry = service.practice(source.getId(), owner);
    var repeated = flow.ordered(retry).get(0);
    assertThat(repeated.getContent()).isEqualTo(source.getContent());
    assertThat(repeated.getRetryOfTurnId()).isEqualTo(source.getId());
    assertThat(repeated.getAnswer()).isNull();
    assertThatThrownBy(() -> service.practice(source.getId(), other)).hasMessageContaining("无权");
    assertThatThrownBy(() -> learning.history(source.getId(), other)).hasMessageContaining("无权");
    service.start(retry, owner);
    doAnswer(i -> assessment(i.getArgument(1), 90, false))
        .when(ai)
        .assess(any(), any(), anyList(), anyBoolean());
    service.answer(retry, repeated.getId(), "改进后的实际回答", owner);
    score(repeated);
    worker.execute(task("REPORT", retry).getId());
    assertThat(learning.history(source.getId(), owner)).hasSize(2);
    assertThat(learning.mistakes(1, 10, null, null, owner).get("total")).isEqualTo(1L);
  }

  @Test
  void reviewDoesNotOverwriteAiScoreAndIsVisibleOnlyToAllowedReaders() throws Exception {
    Long id = completed(80);
    mvc.perform(
            put("/api/interviews/" + id + "/review")
                .header("Authorization", "Bearer " + tokens.issue(owner))
                .contentType("application/json")
                .content("{\"score\":60,\"comment\":\"需要补充例子\"}"))
        .andExpect(status().isForbidden());
    var admin = new CurrentUser(other.id(), "reviewer", "INTERVIEWER");
    mvc.perform(
            put("/api/interviews/" + id + "/review")
                .header("Authorization", "Bearer " + tokens.issue(admin))
                .contentType("application/json")
                .content("{\"score\":60,\"comment\":\"需要补充例子\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.score").value(60));
    assertThat(interviews.selectById(id).getTotalScore()).isEqualTo(80);
    assertThat(reviews.review(id, owner)).isNotNull();
    assertThatThrownBy(() -> reviews.review(id, other)).hasMessageContaining("无权");
  }

  @Test
  void growthOnlyIncludesCompletedInterviewsForCurrentCandidate() {
    completed(40);
    completed(90);
    create(1);
    var data = learning.overview("Java 后端", "MEDIUM", owner);
    assertThat((List<?>) data.get("trend")).hasSize(2);
    assertThat((List<?>) data.get("abilities")).hasSize(1);
    assertThat((List<?>) learning.overview(null, null, other).get("trend")).isEmpty();
    assertThat((List<?>) learning.overview("不存在岗位", null, owner).get("trend")).isEmpty();
  }

  @Test
  void aiGeneratedQuestionRequiresReview() {
    when(ai.questions(anyString(), anyString(), anyString(), anyInt()))
        .thenReturn(List.of(new AiService.QuestionDraft("AI 新问题", "TECHNICAL", "参考要点")));
    var q = bank.generate(new Requests.Generate("新岗位", "描述", "MEDIUM", 1)).get(0);
    assertThat(q.getReviewStatus()).isEqualTo("PENDING");
    assertThat(bank.list(1, 10, "AI 新问题", null, null, null, null, false, owner).getTotal())
        .isZero();
  }

  @Test
  void demoSeedNeverPublishesAnEditedDraft() {
    var draft =
        bank.save(
            null,
            new Requests.QuestionInput(
                "Java 后端",
                "Java 中 equals 与 hashCode 有什么关系？放入 HashMap 时会发生什么？",
                "TECHNICAL",
                "EASY",
                "人工修改的参考要点",
                "JAVA_BACKEND",
                List.of()));
    new com.example.interview.config.DemoData(
            users,
            questions,
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder())
        .run(new org.springframework.boot.DefaultApplicationArguments());
    assertThat(questions.selectById(draft.getId()).getReviewStatus()).isEqualTo("DRAFT");
    assertThat(questions.selectById(draft.getId()).getReferenceAnswer()).isEqualTo("人工修改的参考要点");
  }
}
