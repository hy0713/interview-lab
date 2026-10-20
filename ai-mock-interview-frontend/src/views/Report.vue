<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAuth } from '../stores/auth'
import api from '../api'
import ReviewContent from '../components/ReviewContent.vue'
import ScoreCharts from '../components/ScoreCharts.vue'
import { date, statusLabels } from '../labels'
const id = useRoute().params.id,
  auth = useAuth(),
  data = ref(null),
  review = ref(null),
  loading = ref(true),
  error = ref(false),
  busy = ref(false),
  comment = ref(''),
  humanScore = ref(null)
const scored = computed(
  () => data.value?.turns.filter((t) => t.scoreStatus === 'SUCCEEDED').length || 0
)
const abilities = computed(() => {
  const groups = {}
  for (const t of data.value?.turns || [])
    if (t.scoreStatus === 'SUCCEEDED') (groups[t.category] ??= []).push(t.score)
  return Object.entries(groups).map(([name, s]) => ({
    name,
    score: Math.round(s.reduce((a, b) => a + b, 0) / s.length),
    samples: s.length
  }))
})
async function load() {
  try {
    data.value = await api.get('/interviews/' + id, { silent: true })
    error.value = false
    if (data.value.interview.status === 'COMPLETED') {
      review.value = await api.get('/interviews/' + id + '/review')
      if (review.value) {
        comment.value = review.value.comment
        humanScore.value = review.value.score
      }
    }
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}
async function retry() {
  busy.value = true
  try {
    await api.post('/interviews/' + id + '/retry')
    await load()
  } finally {
    busy.value = false
  }
}
async function save() {
  busy.value = true
  try {
    review.value = await api.put('/interviews/' + id + '/review', {
      score: humanScore.value,
      comment: comment.value
    })
  } finally {
    busy.value = false
  }
}
let timer
onMounted(() => {
  load()
  timer = setInterval(() => {
    if (data.value?.interview.status === 'EVALUATING') load()
  }, 3000)
})
onUnmounted(() => clearInterval(timer))
</script>
<template>
  <div v-loading="loading">
    <div class="page-heading">
      <div>
        <div class="eyebrow">INTERVIEW REVIEW / #{{ id }}</div>
        <h1>从反馈，到下一次进步。</h1>
        <p v-if="data">
          {{ data.interview.position }} · {{ statusLabels[data.interview.status] }} ·
          {{ date(data.interview.createdAt) }}
        </p>
      </div>
      <router-link to="/reports">全部报告 →</router-link>
    </div>
    <el-alert
      v-if="error"
      title="报告加载失败，已保留当前页面内容"
      type="warning"
      :closable="false"
      class="mb"
      ><el-button @click="load">重新加载</el-button></el-alert
    >
    <template v-if="data">
      <section v-if="data.interview.status !== 'COMPLETED'" class="panel mb">
        <h2>报告生成进度</h2>
        <el-progress :percentage="Math.round((scored / Math.max(1, data.turns.length)) * 100)" />
        <p>
          已完成 {{ scored }} / {{ data.turns.length }} 轮评分，报告状态：{{
            data.interview.reportStatus
          }}
        </p>
        <el-alert
          v-for="f in data.failures"
          :key="f.type + '-' + f.targetId"
          :title="f.message"
          type="warning"
          :closable="false"
        /><el-button
          v-if="auth.user?.id === data.interview.candidateId && data.failures.length"
          :loading="busy"
          @click="retry"
          >重试失败任务</el-button
        ><router-link :to="'/interviews/' + id">查看面试进度 →</router-link>
      </section>
      <template v-else>
        <ScoreCharts :abilities="abilities" :show-trend="false" />
        <section class="panel mb">
          <div class="section-heading">
            <h3>人工复核</h3>
            <small class="muted">人工评分与 AI 评分分别展示</small>
          </div>
          <template v-if="auth.user?.role === 'INTERVIEWER'"
            ><el-form label-position="top"
              ><el-form-item label="人工评分（可选）"
                ><el-input-number v-model="humanScore" :min="0" :max="100" /></el-form-item
              ><el-form-item label="面试官评语"
                ><el-input
                  v-model="comment"
                  type="textarea"
                  :rows="3"
                  maxlength="2000"
                  show-word-limit /></el-form-item
              ><el-button type="primary" :loading="busy" :disabled="!comment.trim()" @click="save"
                >保存人工评语</el-button
              ></el-form
            ></template
          >
          <template v-else-if="review"
            ><p class="pre-wrap">{{ review.comment }}</p>
            <p>
              {{ review.score == null ? '未单独打分' : '人工评分：' + review.score + ' 分' }} ·
              {{ review.reviewer_name }} · {{ date(review.updated_at) }}
            </p></template
          >
          <p v-else class="muted">尚无面试官评语。</p>
          <small v-if="auth.user?.role === 'INTERVIEWER' && review"
            >最近保存：{{ review.reviewer_name }} · {{ date(review.updated_at) }}</small
          >
        </section>
      </template>
      <ReviewContent :data="data" />
    </template>
  </div>
</template>
