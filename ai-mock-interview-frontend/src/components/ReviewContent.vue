<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'
import api from '../api'
import { categoryLabels, date } from '../labels'
const props = defineProps({ data: { type: Object, required: true } })
const interview = computed(() => props.data.interview),
  turns = computed(() => props.data.turns),
  report = computed(() => props.data.report)
const router = useRouter(),
  auth = useAuth(),
  history = ref(null),
  leftId = ref(null),
  rightId = ref(null),
  busy = ref(false)
const left = computed(() => history.value?.find((t) => t.id === leftId.value)),
  right = computed(() => history.value?.find((t) => t.id === rightId.value))
async function practice(turn) {
  busy.value = true
  try {
    const next = await api.post('/learning/turns/' + turn.id + '/practice')
    router.push('/interviews/' + next.id)
  } finally {
    busy.value = false
  }
}
async function compare(turn) {
  history.value = await api.get('/learning/turns/' + turn.id + '/history')
  leftId.value = history.value.at(-1)?.id
  rightId.value = history.value[0]?.id
}
function dimensions(turn) {
  if (turn.evaluation?.dimensions)
    return turn.evaluation.dimensions.map((mark) => ({
      ...mark,
      ...turn.rubric.find((rule) => rule.code === mark.code)
    }))
  // V1 已完成报告仍可阅读；它们没有保存原文证据。
  return ['technical', 'logic', 'expression'].map((code, index) => ({
    code,
    label: ['专业正确性', '思路与结构', '表达清晰度'][index],
    weight: [60, 25, 15][index],
    score: turn.evaluation?.[code]
  }))
}
</script>
<template>
  <div>
    <template v-if="report">
      <section class="report-hero">
        <div class="score-ring">
          <strong>{{ interview.totalScore }}</strong
          ><span>综合得分 / 100</span>
        </div>
        <div>
          <div class="eyebrow">YOUR INTERVIEW REVIEW</div>
          <h2>让每一条反馈，都有回答作为依据。</h2>
          <p>{{ report.summary }}</p>
        </div>
      </section>
      <div class="report-columns">
        <section class="panel">
          <div class="eyebrow">KEEP DOING</div>
          <h3>你的优势</h3>
          <ul class="insight-list">
            <li v-for="item in report.strengths" :key="item">{{ item }}</li>
          </ul>
        </section>
        <section class="panel">
          <div class="eyebrow">ROOM TO GROW</div>
          <h3>待提升的方向</h3>
          <ul class="insight-list">
            <li v-for="item in report.weaknesses" :key="item">{{ item }}</li>
          </ul>
        </section>
      </div>
      <section class="panel mb">
        <div class="eyebrow">TURN INSIGHTS INTO ACTION</div>
        <h3>接下来的学习计划</h3>
        <ol class="study-plan">
          <li v-for="(item, index) in report.studyPlan" :key="item">
            <span>{{ String(index + 1).padStart(2, '0') }}</span
            >{{ item }}
          </li>
        </ol>
      </section>
    </template>
    <section class="panel">
      <div class="section-heading">
        <h3>逐题证据与复盘</h3>
        <span class="muted">各主问题含追问分组平均；题型决定评分维度与权重。</span>
      </div>
      <el-collapse
        ><el-collapse-item v-for="turn in turns" :key="turn.id" :name="turn.id"
          ><template #title
            ><div class="review-title">
              <b>{{ turn.rootSequence }}{{ turn.depth ? '.1' : '' }}</b
              ><span>{{ turn.content }}</span
              ><el-tag effect="plain"
                >{{ turn.score ?? '—' }} 分 ·
                {{ turn.depth ? '追问' : categoryLabels[turn.category] }}</el-tag
              >
            </div></template
          >
          <div class="review-body">
            <div class="button-row mb">
              <el-button
                v-if="auth.user?.id === interview.candidateId && interview.status === 'COMPLETED'"
                :loading="busy"
                @click="practice(turn)"
                >同题重新作答</el-button
              >
              <el-button v-if="interview.status === 'COMPLETED'" @click="compare(turn)"
                >查看同题回答对比</el-button
              >
              <el-tag
                v-for="tag in (turn.tags || '').split(',').filter(Boolean)"
                :key="tag"
                effect="plain"
                >{{ tag }}</el-tag
              >
            </div>
            <h4>你的回答</h4>
            <p class="pre-wrap">{{ turn.answer }}</p>
            <template v-if="turn.evaluation">
              <div class="dimension-grid">
                <div v-for="mark in dimensions(turn)" :key="mark.code">
                  <span>{{ mark.label }} · {{ mark.score }} 分 / 权重 {{ mark.weight }}%</span
                  ><el-progress :percentage="mark.score" :show-text="false" color="#36776c" />
                  <p class="dimension-reason">{{ mark.reason }}</p>
                </div>
              </div>
              <h4>评分依据</h4>
              <p>{{ turn.evaluation.feedback }}</p>
              <div
                v-for="(evidence, index) in turn.evaluation.evidence || []"
                :key="index"
                class="evidence-card"
              >
                <el-tag
                  :type="
                    evidence.kind === 'INCORRECT'
                      ? 'danger'
                      : evidence.kind === 'UNCLEAR'
                        ? 'warning'
                        : 'success'
                  "
                  effect="plain"
                  >{{
                    { SUPPORTED: '命中要点', INCORRECT: '存在错误', UNCLEAR: '表述不足' }[
                      evidence.kind
                    ]
                  }}</el-tag
                >
                <blockquote>{{ evidence.quote }}</blockquote>
                <p>{{ evidence.explanation }}</p>
              </div>
              <p v-if="!turn.evaluation.evidence" class="muted">该历史评估未记录原文证据。</p>
              <div v-if="turn.evaluation.coveredPoints" class="point-columns">
                <div>
                  <h4>已命中</h4>
                  <ul class="insight-list">
                    <li v-for="point in turn.evaluation.coveredPoints" :key="point">
                      {{ point }}
                    </li>
                  </ul>
                  <small v-if="!turn.evaluation.coveredPoints.length">暂无明确命中点</small>
                </div>
                <div>
                  <h4>遗漏点</h4>
                  <ul class="insight-list">
                    <li v-for="point in turn.evaluation.missingPoints" :key="point">
                      {{ point }}
                    </li>
                  </ul>
                  <small v-if="!turn.evaluation.missingPoints.length">未发现关键遗漏</small>
                </div>
                <div>
                  <h4>错误点</h4>
                  <ul class="insight-list">
                    <li v-for="point in turn.evaluation.incorrectPoints" :key="point">
                      {{ point }}
                    </li>
                  </ul>
                  <small v-if="!turn.evaluation.incorrectPoints.length">未发现明确错误</small>
                </div>
              </div>
              <h4>改进建议</h4>
              <ul class="insight-list">
                <li v-for="item in turn.evaluation.improvements" :key="item">{{ item }}</li>
              </ul>
            </template>
            <template v-if="turn.referenceAnswer"
              ><h4>参考要点</h4>
              <p class="pre-wrap reference">{{ turn.referenceAnswer }}</p></template
            >
            <p v-if="turn.scoreStatus === 'FAILED'" class="failure-note">
              本轮评分未完成，你的回答已保留。
            </p>
          </div>
        </el-collapse-item></el-collapse
      >
    </section>
    <el-dialog
      :model-value="history !== null"
      title="同题回答对比 · 最近 20 次已完成记录"
      width="85%"
      @close="history = null"
    >
      <el-empty v-if="!history?.length" description="尚无完成的回答" />
      <template v-else>
        <p class="muted">对比自己的实际回答与分数，复练后可在这里查看变化。</p>
        <div class="comparison-grid">
          <section v-for="side in ['left', 'right']" :key="side" class="panel">
            <el-select v-if="side === 'left'" v-model="leftId"
              ><el-option
                v-for="row in history"
                :key="row.id"
                :value="row.id"
                :label="date(row.finished_at) + ' · ' + row.score + ' 分 · #' + row.interview_id"
            /></el-select>
            <el-select v-else v-model="rightId"
              ><el-option
                v-for="row in history"
                :key="row.id"
                :value="row.id"
                :label="date(row.finished_at) + ' · ' + row.score + ' 分 · #' + row.interview_id"
            /></el-select>
            <h4>{{ (side === 'left' ? left : right)?.content }}</h4>
            <h3>{{ (side === 'left' ? left : right)?.score }} 分</h3>
            <p class="pre-wrap">{{ (side === 'left' ? left : right)?.answer }}</p>
          </section>
        </div>
        <p v-if="left && right && left.id !== right.id">
          右侧较左侧 {{ right.score - left.score >= 0 ? '提高' : '降低' }}
          {{ Math.abs(right.score - left.score) }} 分。
        </p>
      </template>
    </el-dialog>
  </div>
</template>
