package com.example.interview.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.interview.ai.AiService;
import com.example.interview.common.BusinessException;
import com.example.interview.domain.Question;
import com.example.interview.mapper.QuestionMapper;
import com.example.interview.security.CurrentUser;
import com.example.interview.web.Requests;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuestionService {
  private final QuestionMapper questions;
  private final AiService ai;
  private final JdbcTemplate db;

  public Page<Question> list(
      int page,
      int size,
      String keyword,
      String difficulty,
      String jobFamily,
      String tag,
      String reviewStatus,
      boolean favorites,
      CurrentUser user) {
    var query =
        Wrappers.<Question>lambdaQuery()
            .and(
                keyword != null && !keyword.isBlank(),
                w ->
                    w.like(Question::getPosition, keyword).or().like(Question::getContent, keyword))
            .eq(difficulty != null && !difficulty.isBlank(), Question::getDifficulty, difficulty)
            .eq(jobFamily != null && !jobFamily.isBlank(), Question::getJobFamily, jobFamily)
            .like(tag != null && !tag.isBlank(), Question::getTags, "," + tag + ",")
            .eq(!user.interviewer(), Question::getReviewStatus, "APPROVED")
            .eq(
                user.interviewer() && reviewStatus != null && !reviewStatus.isBlank(),
                Question::getReviewStatus,
                reviewStatus)
            .orderByDesc(Question::getId);
    if (favorites)
      query.inSql(
          Question::getId, "SELECT question_id FROM mi_favorite WHERE user_id=" + user.id());
    var result = questions.selectPage(new Page<>(page, size), query);
    var ids =
        new HashSet<>(
            db.queryForList(
                "SELECT question_id FROM mi_favorite WHERE user_id=?", Long.class, user.id()));
    for (var q : result.getRecords()) {
      q.setFavorite(ids.contains(q.getId()));
      if (!user.interviewer()) {
        q.setReferenceAnswer(null);
        q.setReviewNote(null);
      }
    }
    return result;
  }

  public List<String> tags(CurrentUser user) {
    var rows =
        questions.selectList(
            Wrappers.<Question>lambdaQuery()
                .select(Question::getTags)
                .eq(!user.interviewer(), Question::getReviewStatus, "APPROVED"));
    var tags = new TreeSet<String>();
    for (var q : rows) for (var tag : q.getTags().split(",")) if (!tag.isBlank()) tags.add(tag);
    return List.copyOf(tags);
  }

  @Transactional
  public Question save(Long id, Requests.QuestionInput input) {
    if (id != null && questions.selectById(id) == null) throw new BusinessException(404, "题目不存在");
    var q = new Question();
    q.setId(id);
    q.setPosition(input.position());
    q.setContent(input.content());
    q.setCategory(input.category());
    q.setDifficulty(input.difficulty());
    q.setReferenceAnswer(input.referenceAnswer());
    q.setJobFamily(input.jobFamily() == null ? "GENERAL" : input.jobFamily());
    q.setTags(
        input.tags() == null || input.tags().isEmpty()
            ? ",未分类,"
            : "," + String.join(",", new LinkedHashSet<>(input.tags())) + ",");
    q.setReviewStatus("DRAFT");
    q.setReviewNote("");
    if (id == null) questions.insert(q);
    else
      questions.update(
          q,
          Wrappers.<Question>lambdaUpdate()
              .eq(Question::getId, id)
              .set(Question::getReviewedBy, null)
              .set(Question::getReviewedAt, null));
    return q;
  }

  public void review(Long id, Requests.QuestionReview input, CurrentUser user) {
    if (questions.update(
            null,
            Wrappers.<Question>lambdaUpdate()
                .eq(Question::getId, id)
                .set(Question::getReviewStatus, input.status())
                .set(Question::getReviewNote, input.note())
                .set(Question::getReviewedBy, user.id())
                .set(Question::getReviewedAt, LocalDateTime.now()))
        == 0) throw new BusinessException(404, "题目不存在");
  }

  public void favorite(Long id, boolean enabled, CurrentUser user) {
    var q = questions.selectById(id);
    if (q == null || (!user.interviewer() && !"APPROVED".equals(q.getReviewStatus())))
      throw new BusinessException(404, "题目不存在");
    if (enabled)
      db.update(
          "INSERT INTO mi_favorite(user_id,question_id) VALUES(?,?) ON DUPLICATE KEY UPDATE"
              + " question_id=VALUES(question_id)",
          user.id(),
          id);
    else db.update("DELETE FROM mi_favorite WHERE user_id=? AND question_id=?", user.id(), id);
  }

  public void delete(Long id) {
    if (questions.deleteById(id) == 0) throw new BusinessException(404, "题目不存在");
  }

  public List<Question> generate(Requests.Generate input) {
    return ai
        .questions(input.position(), input.jobDescription(), input.difficulty(), input.count())
        .stream()
        .map(
            d -> {
              var q = new Question();
              q.setPosition(input.position());
              q.setDifficulty(input.difficulty());
              q.setContent(d.content());
              q.setCategory(d.category());
              q.setReferenceAnswer(d.referenceAnswer());
              q.setJobFamily("GENERAL");
              q.setTags(",未分类,");
              q.setReviewStatus("PENDING");
              questions.insert(q);
              return q;
            })
        .toList();
  }

  public List<Question> pick(
      String position, String difficulty, int count, String jobFamily, String tag) {
    var pool =
        new ArrayList<>(
            questions.selectList(
                Wrappers.<Question>lambdaQuery()
                    .eq(Question::getReviewStatus, "APPROVED")
                    .like(jobFamily == null || jobFamily.isBlank(), Question::getPosition, position)
                    .eq(
                        jobFamily != null && !jobFamily.isBlank(),
                        Question::getJobFamily,
                        jobFamily)
                    .like(tag != null && !tag.isBlank(), Question::getTags, "," + tag + ",")
                    .eq(Question::getDifficulty, difficulty)
                    .last("LIMIT 500")));
    if (pool.size() < count) throw new BusinessException("已审核且匹配岗位、知识点与难度的题目不足，请减少题数或调整筛选");
    Collections.shuffle(pool);
    return pool.subList(0, count);
  }
}
