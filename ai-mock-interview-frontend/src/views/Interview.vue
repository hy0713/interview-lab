<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'
import ReviewContent from '../components/ReviewContent.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { statusLabels, statusType, difficultyLabels, categoryLabels, date } from '../labels'
const id = useRoute().params.id
const router = useRouter(),
  auth = useAuth()
const canOperate = computed(() => auth.user?.id === interview.value?.candidateId)
const data = ref(null),
  loading = ref(true),
  busy = ref(false),
  answer = ref(''),
  now = ref(Date.now()),
  offset = ref(0),
  connectionError = ref(false)
const interview = computed(() => data.value?.interview)
const turns = computed(() => data.value?.turns || [])
const current = computed(() => turns.value.find((t) => ['ACTIVE', 'PAUSED'].includes(t.status)))
const active = computed(() => (current.value?.status === 'ACTIVE' ? current.value : null))
const nextTurn = computed(() => turns.value.find((t) => t.status === 'WAITING'))
const report = computed(() => data.value?.report)
const previous = computed(() =>
  turns.value.filter((t) => ['ANSWERED', 'TIMEOUT'].includes(t.status))
)
const hasFailed = computed(
  () =>
    turns.value.some((t) => t.scoreStatus === 'FAILED') ||
    interview.value?.reportStatus === 'FAILED'
)
const failures = computed(() => data.value?.failures || [])
const mutable = computed(
  () => canOperate.value && ['CREATED', 'IN_PROGRESS', 'PAUSED'].includes(interview.value?.status)
)
const remaining = computed(() => {
  if (current.value?.status === 'PAUSED') return Math.ceil(current.value.remainingMillis / 1000)
  return active.value
    ? Math.max(
        0,
        Math.ceil((new Date(active.value.deadline).getTime() - now.value - offset.value) / 1000)
      )
    : 0
})
const clock = computed(
  () =>
    String(Math.floor(remaining.value / 60)).padStart(2, '0') +
    ':' +
    String(remaining.value % 60).padStart(2, '0')
)
function label(turn) {
  return '主问题 ' + turn.rootSequence + (turn.depth ? ' · 追问' : '')
}
function apply(value) {
  now.value = Date.now()
  data.value = value
  if (value.interview.status === 'COMPLETED') router.replace('/reports/' + id)
  offset.value = new Date(value.serverTime).getTime() - now.value
  connectionError.value = false
}
async function load(silent = false) {
  try {
    apply(await api.get('/interviews/' + id, { silent }))
  } catch {
    connectionError.value = true
  } finally {
    loading.value = false
  }
}
watch(
  () => current.value?.id,
  (turnId) => {
    answer.value = turnId ? localStorage.getItem('interview-draft-' + id + '-' + turnId) || '' : ''
  }
)
watch(answer, (value) => {
  if (current.value) localStorage.setItem('interview-draft-' + id + '-' + current.value.id, value)
})
async function action(path, payload) {
  busy.value = true
  try {
    apply(await api.post('/interviews/' + id + path, payload))
  } finally {
    busy.value = false
  }
}
async function submit() {
  if (!answer.value.trim() || !active.value) return
  const turnId = active.value.id
  await action('/turns/' + turnId + '/answer', { answer: answer.value })
  localStorage.removeItem('interview-draft-' + id + '-' + turnId)
  ElMessage[
    data.value.turns.find((t) => t.id === turnId)?.status === 'TIMEOUT' ? 'warning' : 'success'
  ](
    data.value.turns.find((t) => t.id === turnId)?.status === 'TIMEOUT'
      ? '该题已超时，请确认后开始下一题'
      : '答案已保存，正在分析是否需要追问'
  )
}
async function finish() {
  try {
    await ElMessageBox.confirm(
      '交卷后不会再追加追问，未作答的题目按 0 分计算。确定交卷？',
      '提前交卷',
      { type: 'warning', confirmButtonText: '确认交卷' }
    )
  } catch {
    return
  }
  await action('/finish')
}
async function cancel() {
  try {
    await ElMessageBox.confirm(
      '取消后保留已有回答，不再继续面试或生成报告。确定取消？',
      '取消本场面试',
      { type: 'warning', confirmButtonText: '确认取消' }
    )
  } catch {
    return
  }
  await action('/cancel')
}
let tick, poll
onMounted(() => {
  load()
  tick = setInterval(() => {
    now.value = Date.now()
  }, 500)
  poll = setInterval(() => {
    if (!busy.value && !['COMPLETED', 'CANCELLED'].includes(interview.value?.status)) load(true)
  }, 2500)
})
onUnmounted(() => {
  clearInterval(tick)
  clearInterval(poll)
})
</script>

<template>
  <div v-loading="loading">
    <template v-if="interview">
      <div class="page-heading">
        <div>
          <div class="eyebrow">ADAPTIVE INTERVIEW / #{{ interview.id }}</div>
          <h1>{{ interview.position }}</h1>
          <p>
            {{ difficultyLabels[interview.difficulty] }} · {{ data.mainQuestionCount }} 道主问题 ·
            {{ interview.mode === 'EXAM' ? '模拟模式' : '练习模式' }} ·
            {{ date(interview.createdAt) }}
          </p>
        </div>
        <div class="button-row session-actions">
          <el-tag :type="statusType(interview.status)" size="large" effect="plain">{{
            statusLabels[interview.status]
          }}</el-tag>
          <el-button
            v-if="canOperate && interview.status === 'IN_PROGRESS' && interview.mode === 'PRACTICE'"
            :disabled="busy"
            @click="action('/pause')"
            >暂停面试</el-button
          >
          <el-button
            v-if="canOperate && ['IN_PROGRESS', 'PAUSED'].includes(interview.status)"
            :disabled="busy"
            @click="finish"
            >提前交卷</el-button
          >
          <el-button v-if="mutable" :disabled="busy" @click="cancel">取消面试</el-button>
          <router-link to="/interviews">返回记录 →</router-link>
        </div>
      </div>
      <el-alert
        v-if="connectionError"
        title="暂时无法同步状态，正在重试。请勿重复提交答案。"
        type="warning"
        :closable="false"
        class="mb"
      />
      <section v-if="!canOperate" class="panel mb">
        <h2>候选人进度</h2>
        <p>当前阶段：{{ interview.phase }}，已保存 {{ previous.length }} 次回答。</p>
        <p class="muted">候选人本人完成作答；报告生成后可进行人工复核。</p>
      </section>
      <section v-else-if="interview.status === 'CREATED'" class="panel ready-panel">
        <div class="ready-symbol">↗</div>
        <div class="eyebrow">THINK, EXPLAIN, GO DEEPER</div>
        <h2>一场会根据回答深入的面试。</h2>
        <p>
          每题作答 {{ interview.secondsPerQuestion / 60 }} 分钟；每道主问题最多追问一次，全场最多
          {{ interview.maxFollowUps }} 次。<br />
          AI 分析期间不计时；下一题由你确认开始。刷新页面会恢复当前进度与本地草稿。<br />
          {{
            interview.mode === 'PRACTICE'
              ? '练习模式可暂停计时，离开前请点击“暂停面试”。'
              : '模拟模式不可暂停当前题，离开页面也会继续计时；到期后等待你确认下一题。'
          }}
        </p>
        <el-button type="primary" size="large" :loading="busy" @click="action('/start')"
          >开始第一题 →</el-button
        >
      </section>
      <section v-else-if="interview.status === 'PAUSED'" class="panel ready-panel">
        <div class="eyebrow">TAKE YOUR TIME</div>
        <h2>面试已暂停</h2>
        <p>
          {{
            current
              ? '当前题剩余 ' + clock + '，暂停期间不会消耗答题时间。'
              : '已保留面试进度，恢复后再开始下一题。'
          }}<br />已提交回答的 AI 分析会继续处理。
        </p>
        <el-button type="primary" :loading="busy" size="large" @click="action('/resume')"
          >恢复面试 →</el-button
        >
      </section>
      <section v-else-if="interview.status === 'CANCELLED'" class="panel evaluation-panel">
        <h2>这场面试已取消</h2>
        <p class="muted">已有回答保留在下方，不再追加问题或生成完整报告。</p>
      </section>
      <template v-else-if="interview.status === 'IN_PROGRESS'">
        <div v-if="active" class="interview-grid">
          <section class="panel answer-panel">
            <div class="question-top">
              <span class="eyebrow"
                >{{ label(active) }} / {{ categoryLabels[active.category] }}</span
              ><span class="timer" :class="{ urgent: remaining <= 30 }">{{ clock }}</span>
            </div>
            <el-progress
              :percentage="Math.min(100, (remaining / interview.secondsPerQuestion) * 100)"
              :show-text="false"
              :stroke-width="4"
              :color="remaining <= 30 ? '#c26452' : '#36776c'"
            />
            <div v-if="active.depth" class="follow-up-note">
              <b>面试官追问</b>
              <p>{{ active.followUpReason }}</p>
            </div>
            <h2 class="active-question">{{ active.content }}</h2>
            <p class="muted">用自己的话说明结论、依据和具体场景。</p>
            <el-input
              v-model="answer"
              type="textarea"
              :rows="11"
              resize="vertical"
              maxlength="12000"
              show-word-limit
              placeholder="写下你的思路与回答……"
              :disabled="busy"
            />
            <div class="answer-footer">
              <small>{{
                interview.mode === 'PRACTICE'
                  ? '离开前可暂停；草稿保存在本浏览器'
                  : '当前题持续计时；草稿保存在本浏览器'
              }}</small
              ><el-button
                type="primary"
                size="large"
                :loading="busy"
                :disabled="!answer.trim() || remaining === 0"
                @click="submit"
                >提交回答 →</el-button
              >
            </div>
            <p v-if="remaining === 0" class="muted">本题时间已到，正在同步状态……</p>
          </section>
          <aside class="panel progress-panel">
            <div class="eyebrow">SESSION MAP</div>
            <h3>面试进度</h3>
            <div
              v-for="turn in turns"
              :key="turn.id"
              class="turn-step"
              :class="{ current: turn.status === 'ACTIVE' }"
            >
              <span>{{ turn.rootSequence }}{{ turn.depth ? '.1' : '' }}</span>
              <div>
                <b>{{ turn.depth ? '针对回答的追问' : categoryLabels[turn.category] }}</b
                ><small>{{ statusLabels[turn.status] }}</small>
              </div>
            </div>
            <div class="soft-note">
              每次分析只围绕已提交的回答。参考答案与评分依据会在交卷后展示。
            </div>
          </aside>
        </div>
        <section v-else-if="interview.phase === 'READY'" class="panel ready-panel">
          <div class="eyebrow">
            {{ nextTurn?.depth ? 'LET US GO A LITTLE DEEPER' : 'READY FOR THE NEXT TOPIC' }}
          </div>
          <h2>
            {{ nextTurn?.depth ? '面试官想进一步了解你的回答。' : '准备好开始下一道主问题了吗？' }}
          </h2>
          <p>
            {{
              nextTurn?.depth
                ? '接下来是针对上一题的追问。'
                : '上一轮已经结束，下一题将进入新的主题。'
            }}<br />点击后才开始该题计时。
          </p>
          <el-button type="primary" size="large" :loading="busy" @click="action('/next')"
            >{{ nextTurn?.depth ? '开始追问' : '开始下一题' }} →</el-button
          >
        </section>
        <section v-else class="panel evaluation-panel">
          <div class="eyebrow">LISTENING TO YOUR ANSWER</div>
          <h2>{{ hasFailed ? '本轮分析暂未完成' : '正在理解你的回答，决定下一步。' }}</h2>
          <p class="muted">
            {{
              hasFailed
                ? '回答已保存，可以重试本轮分析。'
                : '此时不计答题时间。面试官会根据你的回答决定继续追问或进入下一个主题。'
            }}
          </p>
          <el-alert
            v-for="failure in failures"
            :key="failure.type + '-' + failure.targetId"
            :title="failure.message"
            type="warning"
            :closable="false"
            class="mb"
          />
          <el-button v-if="hasFailed" type="primary" :loading="busy" @click="action('/retry')"
            >重试分析</el-button
          >
        </section>
      </template>
      <section
        v-if="previous.length && ['IN_PROGRESS', 'PAUSED'].includes(interview.status)"
        class="panel dialogue-panel"
      >
        <div class="section-heading">
          <h3>本场对话</h3>
          <span class="muted">已保存 {{ previous.length }} 次回答</span>
        </div>
        <article v-for="turn in previous" :key="turn.id" class="dialogue-turn">
          <small>{{ label(turn) }} · {{ statusLabels[turn.status] }}</small>
          <h4>{{ turn.content }}</h4>
          <p class="pre-wrap">{{ turn.answer }}</p>
        </article>
      </section>
      <template v-if="['EVALUATING', 'COMPLETED', 'CANCELLED'].includes(interview.status)">
        <section v-if="interview.status === 'EVALUATING'" class="panel evaluation-panel">
          <span class="eyebrow">REFLECTION IN PROGRESS</span>
          <h2>{{ hasFailed ? '部分评估需要重试' : '面试结束，正在整理完整反馈。' }}</h2>
          <div class="task-chips">
            <el-tag
              v-for="turn in turns"
              :key="turn.id"
              :type="statusType(turn.scoreStatus)"
              effect="plain"
              >{{ label(turn) }} · {{ statusLabels[turn.scoreStatus] }}</el-tag
            >
          </div>
          <p class="muted">
            报告：{{
              interview.reportStatus === 'WAITING'
                ? '等待所有回答分析完成'
                : statusLabels[interview.reportStatus]
            }}
          </p>
          <el-alert
            v-for="failure in failures"
            :key="failure.type + '-' + failure.targetId"
            :title="failure.message"
            type="warning"
            :closable="false"
            class="mb"
          />
          <el-button v-if="hasFailed" type="primary" :loading="busy" @click="action('/retry')"
            >重试失败任务</el-button
          >
        </section>
        <ReviewContent :data="data" />
      </template>
    </template>
    <el-empty v-else-if="!loading" description="未能加载面试"
      ><el-button @click="load()">重新加载</el-button></el-empty
    >
  </div>
</template>
