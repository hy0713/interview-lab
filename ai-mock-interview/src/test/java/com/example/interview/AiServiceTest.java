package com.example.interview;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.interview.ai.*;
import com.example.interview.common.Jsons;
import com.example.interview.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;

class AiServiceTest {
  private final ObjectMapper json = new ObjectMapper();
  private final jakarta.validation.ValidatorFactory validators =
      Validation.buildDefaultValidatorFactory();
  private final ChatModel model = mock(ChatModel.class);
  private final AiService service =
      new AiService(
          ChatClient.builder(model),
          validators.getValidator(),
          new Jsons(json),
          "test-key",
          "test-model");

  @AfterEach
  void close() {
    validators.close();
  }

  void respond(Object value) throws Exception {
    when(model.call(any(Prompt.class)))
        .thenReturn(
            new ChatResponse(
                List.of(new Generation(new AssistantMessage(json.writeValueAsString(value))))));
  }

  AiService.Evaluation evaluation(String category, String quote, int... scores) {
    var rules = ScoreRubrics.forCategory(category);
    var marks = new ArrayList<AiService.DimensionMark>();
    for (int i = 0; i < 3; i++)
      marks.add(new AiService.DimensionMark(rules.get(i).code(), scores[i], "对应回答中的表现"));
    return new AiService.Evaluation(
        marks,
        "整体评价",
        List.of(new AiService.Evidence(quote, "SUPPORTED", "原文命中了部分要点")),
        List.of("已命中要点"),
        List.of("遗漏要点"),
        List.of(),
        List.of("补充实际例子"));
  }

  Turn turn(String answer) {
    var turn = new Turn();
    turn.setId(2L);
    turn.setCategory("TECHNICAL");
    turn.setContent("为什么事务可能失效？");
    turn.setReferenceAnswer("代理与自调用");
    turn.setAnswer(answer);
    return turn;
  }

  Interview interview() {
    var interview = new Interview();
    interview.setPosition("Java 后端");
    interview.setJobDescription("校招");
    interview.setDifficulty("MEDIUM");
    return interview;
  }

  @Test
  void structuredQuestionsAreConvertedAndCountIsChecked() throws Exception {
    respond(
        Map.of(
            "questions",
            List.of(new AiService.QuestionDraft("解释 HashMap", "TECHNICAL", "哈希表与键比较"))));
    assertThat(service.questions("Java", "应届生", "MEDIUM", 1)).hasSize(1);
    assertThatThrownBy(() -> service.questions("Java", "应届生", "MEDIUM", 2))
        .hasMessageContaining("数量不符");
  }

  @Test
  void invalidQuestionsAndMissingScoresAreRejected() throws Exception {
    respond(
        Map.of(
            "questions",
            List.of(Map.of("content", "", "category", "UNKNOWN", "referenceAnswer", "参考"))));
    assertThatThrownBy(() -> service.questions("Java", "校招", "MEDIUM", 1))
        .hasMessageContaining("格式");
    respond(
        Map.of(
            "evaluation",
            Map.of("feedback", "缺少维度分"),
            "followUp",
            Map.of("required", false, "reason", "结束")));
    assertThatThrownBy(() -> service.assess(interview(), turn("回答"), List.of(), true))
        .hasMessageContaining("格式");
  }

  @Test
  void differentQuestionCategoriesUseDifferentRubrics() {
    assertThat(ScoreRubrics.score("TECHNICAL", evaluation("TECHNICAL", "原文", 80, 60, 100)))
        .isEqualTo(78);
    assertThat(ScoreRubrics.score("BEHAVIORAL", evaluation("BEHAVIORAL", "原文", 40, 80, 100)))
        .isEqualTo(69);
    assertThat(ScoreRubrics.score("SCENARIO", evaluation("SCENARIO", "原文", 40, 80, 100)))
        .isEqualTo(69);
    assertThatThrownBy(
            () -> ScoreRubrics.score("BEHAVIORAL", evaluation("TECHNICAL", "原文", 80, 80, 80)))
        .hasMessageContaining("维度");
  }

  @Test
  void evidenceMustBeExactQuoteFromCurrentAnswer() throws Exception {
    respond(
        new AiService.Assessment(
            evaluation("TECHNICAL", "模型编造的句子", 60, 60, 60),
            new AiService.FollowUp(false, "结束", null)));
    assertThatThrownBy(() -> service.assess(interview(), turn("我会拆分到另外一个 Bean"), List.of(), true))
        .hasMessageContaining("不存在的原文");
  }

  @Test
  void validAssessmentCarriesDialogueAndChecksFollowUp() throws Exception {
    var previous = turn("之前的回答：使用了代理");
    previous.setId(1L);
    var current = turn("同类调用可能绕过代理");
    respond(
        new AiService.Assessment(
            evaluation("TECHNICAL", "绕过代理", 70, 70, 70),
            new AiService.FollowUp(
                true,
                "验证解决方案",
                new AiService.QuestionDraft(
                    "你会怎样验证拆分 Bean 后事务确实回滚？", "TECHNICAL", "观察事务日志并验证数据回滚"))));
    var result = service.assess(interview(), current, List.of(previous), true);
    assertThat(result.followUp().required()).isTrue();
    var prompt = ArgumentCaptor.forClass(Prompt.class);
    verify(model).call(prompt.capture());
    assertThat(prompt.getValue().getContents()).contains("之前的回答", "同类调用可能绕过代理", "mayFollowUp");
  }

  @Test
  void followUpCannotRepeatCurrentQuestionOrChangeCategory() throws Exception {
    var current = turn("回答");
    respond(
        new AiService.Assessment(
            evaluation("TECHNICAL", "回答", 50, 50, 50), new AiService.FollowUp(true, "追问", null)));
    assertThatThrownBy(() -> service.assess(interview(), current, List.of(), true))
        .hasMessageContaining("追问");
    respond(
        new AiService.Assessment(
            evaluation("TECHNICAL", "回答", 50, 50, 50),
            new AiService.FollowUp(
                true, "追问", new AiService.QuestionDraft(current.getContent(), "TECHNICAL", "要点"))));
    assertThatThrownBy(() -> service.assess(interview(), current, List.of(), true))
        .hasMessageContaining("追问");
  }
}
