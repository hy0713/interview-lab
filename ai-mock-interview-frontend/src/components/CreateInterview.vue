<script setup>
import { ref, reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import { useAuth } from '../stores/auth'
import { jobFamilies } from '../labels'
const props = defineProps({ initialTag: { type: String, default: '' } })
const auth = useAuth(),
  candidates = ref([]),
  tags = ref([])
async function findCandidates(keyword = '') {
  candidates.value = (
    await api.get('/management/candidates', { params: { keyword, size: 50 } })
  ).records
}
const open = defineModel({ type: Boolean })
const emit = defineEmits(['created'])
const router = useRouter(),
  busy = ref(false)
const form = reactive({
  position: 'Java 后端',
  jobDescription:
    '面向应届生的 Java 后端岗位，考察 Java 基础、Spring Boot、MySQL、Redis 和项目实践。',
  difficulty: 'MEDIUM',
  source: 'BANK',
  count: 5,
  secondsPerQuestion: 180,
  mode: 'PRACTICE',
  maxFollowUps: 2,
  jobFamily: 'JAVA_BACKEND',
  tag: '',
  candidateId: null
})
watch(open, async (value) => {
  if (!value) busy.value = false
  else {
    form.tag = props.initialTag
    form.count = props.initialTag ? 1 : 5
    tags.value = await api.get('/questions/tags')
    if (auth.user?.role === 'INTERVIEWER') await findCandidates()
  }
})
async function create() {
  busy.value = true
  try {
    const result = await api.post('/interviews', { ...form, tag: form.tag || null })
    open.value = false
    emit('created')
    router.push('/interviews/' + result.id)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <el-dialog
    v-model="open"
    title="创建一场面试练习"
    width="560px"
    :close-on-click-modal="!busy"
    :close-on-press-escape="!busy"
    :show-close="!busy"
  >
    <el-form label-position="top" @submit.prevent="create">
      <el-form-item v-if="auth.user?.role === 'INTERVIEWER'" label="指派候选人"
        ><el-select
          v-model="form.candidateId"
          filterable
          remote
          :remote-method="findCandidates"
          placeholder="输入用户名搜索候选人"
          ><el-option
            v-for="u in candidates"
            :key="u.id"
            :label="u.username"
            :value="u.id" /></el-select
      ></el-form-item>
      <el-form-item label="目标岗位"
        ><el-input v-model="form.position" maxlength="100" placeholder="例如：Java 后端"
      /></el-form-item>
      <el-form-item label="出题方式"
        ><el-radio-group v-model="form.source"
          ><el-radio-button label="BANK">从题库抽题</el-radio-button
          ><el-radio-button label="AI">AI 定制出题</el-radio-button></el-radio-group
        ></el-form-item
      >
      <div v-if="form.source === 'BANK'" class="form-grid">
        <el-form-item label="岗位分类"
          ><el-select v-model="form.jobFamily"
            ><el-option
              v-for="(label, key) in jobFamilies"
              :key="key"
              :value="key"
              :label="label" /></el-select></el-form-item
        ><el-form-item label="知识点组卷"
          ><el-select v-model="form.tag" clearable filterable placeholder="全部知识点"
            ><el-option v-for="tag in tags" :key="tag" :label="tag" :value="tag" /></el-select
        ></el-form-item>
      </div>
      <el-form-item label="岗位描述 / 练习重点"
        ><el-input
          v-model="form.jobDescription"
          type="textarea"
          :rows="3"
          maxlength="6000"
          show-word-limit
      /></el-form-item>
      <div class="form-grid">
        <el-form-item label="难度"
          ><el-select v-model="form.difficulty"
            ><el-option label="基础" value="EASY" /><el-option
              label="进阶"
              value="MEDIUM" /><el-option label="挑战" value="HARD" /></el-select></el-form-item
        ><el-form-item label="题目数量"
          ><el-input-number v-model="form.count" :min="1" :max="10"
        /></el-form-item>
      </div>
      <el-form-item label="每题作答时间（秒）"
        ><el-input-number v-model="form.secondsPerQuestion" :min="60" :max="600" :step="30"
      /></el-form-item>
      <el-form-item label="面试模式"
        ><el-radio-group v-model="form.mode"
          ><el-radio-button label="PRACTICE">练习：可暂停</el-radio-button
          ><el-radio-button label="EXAM">模拟：作答不可暂停</el-radio-button></el-radio-group
        ></el-form-item
      >
      <el-form-item label="全场追问上限（每道主问题最多追问一次）"
        ><el-input-number v-model="form.maxFollowUps" :min="0" :max="3"
      /></el-form-item>
      <div class="soft-note">
        {{
          form.source === 'AI'
            ? 'AI 将根据岗位描述生成问题，通常需要一些时间，请耐心等待。'
            : '从已审核且匹配分类、难度和知识点的题目中抽取；题目不足时可减少题数。'
        }}
      </div> </el-form
    ><template #footer
      ><el-button :disabled="busy" @click="open = false">取消</el-button
      ><el-button
        type="primary"
        :loading="busy"
        :disabled="
          !form.position.trim() || (auth.user?.role === 'INTERVIEWER' && !form.candidateId)
        "
        @click="create"
        >创建面试 →</el-button
      ></template
    >
  </el-dialog>
</template>
