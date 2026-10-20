package com.example.interview.ai;

import com.example.interview.common.BusinessException;
import java.util.*;
import java.util.stream.Collectors;

public final class ScoreRubrics {
  public static final int FOLLOW_UP_STOP_SCORE = 85;

  public record Dimension(String code, String label, int weight, String standard) {}

  private ScoreRubrics() {}

  public static boolean warrantsFollowUp(String category, AiService.Assessment assessment) {
    if (!assessment.followUp().required()) return false;
    var evaluation = assessment.evaluation();
    boolean gap =
        !evaluation.missingPoints().isEmpty()
            || !evaluation.incorrectPoints().isEmpty()
            || evaluation.evidence().stream().anyMatch(e -> "UNCLEAR".equals(e.kind()));
    return gap
        && (score(category, evaluation) < FOLLOW_UP_STOP_SCORE
            || !evaluation.incorrectPoints().isEmpty());
  }

  public static List<Dimension> forCategory(String category) {
    return switch (category) {
      case "BEHAVIORAL" ->
          List.of(
              new Dimension("specificity", "情境与行动细节", 40, "是否描述具体情境、目标和实际行动，避免空泛表态"),
              new Dimension("ownership", "个人贡献与结果", 35, "是否说清本人职责、取舍及可验证结果，不把团队成果全部归给自己"),
              new Dimension("reflection", "反思与迁移", 25, "是否解释教训、改进和以后如何应用"));
      case "SCENARIO" ->
          List.of(
              new Dimension("analysis", "问题分析", 40, "是否识别目标、约束、关键风险与需要澄清的信息"),
              new Dimension("decision", "方案与执行", 35, "是否给出可行步骤、优先级与验证办法"),
              new Dimension("tradeoff", "取舍与风险", 25, "是否比较方案，解释代价、边界和风险处置"));
      case "TECHNICAL" ->
          List.of(
              new Dimension("correctness", "专业正确性", 60, "原理、概念和关键结论是否准确，是否出现实质性错误"),
              new Dimension("reasoning", "推理与实践", 25, "是否解释因果、边界条件并结合恰当示例"),
              new Dimension("expression", "表达结构", 15, "是否结论明确、层次清晰且围绕问题回答"));
      default -> throw new IllegalArgumentException("未知题型: " + category);
    };
  }

  public static int score(String category, AiService.Evaluation evaluation) {
    var rubric = forCategory(category);
    var marks = evaluation.dimensions();
    var expected = rubric.stream().map(Dimension::code).collect(Collectors.toSet());
    if (marks.size() != rubric.size()
        || !marks.stream()
            .map(AiService.DimensionMark::code)
            .collect(Collectors.toSet())
            .equals(expected)) throw new BusinessException(502, "AI 评分维度与题型不符，请重试");
    Map<String, Integer> scores =
        marks.stream()
            .collect(
                Collectors.toMap(AiService.DimensionMark::code, AiService.DimensionMark::score));
    return (int)
        Math.round(
            rubric.stream().mapToDouble(d -> scores.get(d.code()) * d.weight() / 100.0).sum());
  }
}
