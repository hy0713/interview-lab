<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuth } from '../stores/auth'
import api from '../api'
import { difficultyLabels, categoryLabels, jobFamilies, reviewLabels } from '../labels'
const auth = useAuth(),
  canEdit = computed(() => auth.user?.role === 'INTERVIEWER'),
  rows = ref([]),
  total = ref(0),
  page = ref(1),
  loading = ref(false),
  failed = ref(false),
  tags = ref([])
const filters = reactive({
  keyword: '',
  difficulty: '',
  jobFamily: '',
  tag: '',
  reviewStatus: '',
  favorites: false
})
const editOpen = ref(false),
  aiOpen = ref(false),
  saving = ref(false),
  editingId = ref(null),
  viewing = ref(null),
  reviewing = ref(null),
  reviewNote = ref(''),
  tagText = ref('')
const form = reactive({
  position: 'Java 后端',
  content: '',
  category: 'TECHNICAL',
  difficulty: 'MEDIUM',
  referenceAnswer: '',
  jobFamily: 'JAVA_BACKEND'
})
const aiForm = reactive({
  position: 'Java 后端',
  jobDescription: '面向应届生，考察 Java、Spring Boot、MySQL、Redis 和项目实践。',
  difficulty: 'MEDIUM',
  count: 3
})
async function load() {
  loading.value = true
  try {
    const [result, tagList] = await Promise.all([
      api.get('/questions', { params: { ...filters, page: page.value, size: 10 } }),
      api.get('/questions/tags')
    ])
    rows.value = result.records
    total.value = result.total
    tags.value = tagList
    failed.value = false
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
function search() {
  page.value = 1
  load()
}
function edit(row) {
  editingId.value = row?.id || null
  Object.assign(
    form,
    row || {
      position: 'Java 后端',
      content: '',
      category: 'TECHNICAL',
      difficulty: 'MEDIUM',
      referenceAnswer: '',
      jobFamily: 'JAVA_BACKEND'
    }
  )
  tagText.value = (row?.tags || '').split(',').filter(Boolean).join('，')
  editOpen.value = true
}
async function save() {
  if (!form.position.trim() || !form.content.trim() || !form.referenceAnswer.trim())
    return ElMessage.warning('请填写岗位、题目和参考要点')
  saving.value = true
  try {
    const payload = {
      ...form,
      tags: tagText.value
        .split(/[,，]/)
        .map((t) => t.trim())
        .filter(Boolean)
    }
    if (editingId.value) await api.put('/questions/' + editingId.value, payload)
    else await api.post('/questions', payload)
    editOpen.value = false
    ElMessage.success('已保存草稿，请审核后发布')
    await load()
  } finally {
    saving.value = false
  }
}
function openReview(row) {
  reviewing.value = row
  reviewNote.value = ''
}
async function review(status) {
  saving.value = true
  try {
    await api.post('/questions/' + reviewing.value.id + '/review', {
      status,
      note: reviewNote.value
    })
    reviewing.value = null
    await load()
  } finally {
    saving.value = false
  }
}
async function favorite(row) {
  if (row.favorite) await api.delete('/questions/' + row.id + '/favorite')
  else await api.put('/questions/' + row.id + '/favorite')
  await load()
}
async function remove(row) {
  try {
    await ElMessageBox.confirm('删除不影响已有面试快照。确定删除？', '删除题目', {
      type: 'warning'
    })
  } catch {
    return
  }
  await api.delete('/questions/' + row.id)
  load()
}
async function generate() {
  saving.value = true
  try {
    await api.post('/questions/generate', aiForm)
    aiOpen.value = false
    ElMessage.success('AI 题目已保存为待审核状态')
    search()
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
<template>
  <div>
    <div class="page-heading">
      <div>
        <div class="eyebrow">BUILD YOUR KNOWLEDGE</div>
        <h1>{{ canEdit ? '维护有体系的面试题库。' : '按知识点，找到下一道好问题。' }}</h1>
        <p>
          {{
            canEdit
              ? '编辑与 AI 出题先保存草稿，审核通过后进入候选人题库。'
              : '筛选岗位、知识点，收藏需要反复练习的问题。'
          }}
        </p>
      </div>
      <div v-if="canEdit" class="button-row">
        <el-button @click="aiOpen = true">✳ AI 出题</el-button
        ><el-button type="primary" @click="edit(null)">＋ 添加题目</el-button>
      </div>
    </div>
    <section class="panel">
      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          placeholder="岗位或题目"
          clearable
          style="max-width: 240px"
          @keyup.enter="search"
        /><el-select placeholder="全部岗位分类" v-model="filters.jobFamily" style="width: 140px"
          ><el-option label="全部岗位分类" value="" /><el-option
            v-for="(v, k) in jobFamilies"
            :key="k"
            :label="v"
            :value="k" /></el-select
        ><el-select
          v-model="filters.tag"
          clearable
          filterable
          style="width: 150px"
          placeholder="知识点"
          ><el-option v-for="t in tags" :key="t" :value="t" :label="t" /></el-select
        ><el-select placeholder="全部难度" v-model="filters.difficulty" style="width: 130px"
          ><el-option label="全部难度" value="" /><el-option
            v-for="(v, k) in difficultyLabels"
            :key="k"
            :value="k"
            :label="v" /></el-select
        ><el-select
          v-if="canEdit"
          placeholder="全部审核状态"
          v-model="filters.reviewStatus"
          style="width: 140px"
          ><el-option label="全部审核状态" value="" /><el-option
            v-for="(v, k) in reviewLabels"
            :key="k"
            :value="k"
            :label="v" /></el-select
        ><el-checkbox v-model="filters.favorites">仅收藏</el-checkbox
        ><el-button @click="search">搜索</el-button>
      </div>
      <el-alert v-if="failed" title="加载失败，请重试" type="warning"
        ><el-button @click="load">重新加载</el-button></el-alert
      >
      <el-table v-loading="loading" :data="rows"
        ><el-table-column label="题目 / 知识点" min-width="320"
          ><template #default="{ row }"
            ><button class="question-link" @click="viewing = row">{{ row.content }}</button>
            <div class="tag-row">
              <el-tag
                v-for="t in row.tags.split(',').filter(Boolean)"
                :key="t"
                size="small"
                effect="plain"
                >{{ t }}</el-tag
              >
            </div></template
          ></el-table-column
        ><el-table-column label="岗位分类" min-width="120"
          ><template #default="{ row }"
            >{{ jobFamilies[row.jobFamily]
            }}<small class="block muted">{{ row.position }}</small></template
          ></el-table-column
        ><el-table-column label="题型" width="100"
          ><template #default="{ row }">{{
            categoryLabels[row.category]
          }}</template></el-table-column
        ><el-table-column label="难度" width="80"
          ><template #default="{ row }">{{
            difficultyLabels[row.difficulty]
          }}</template></el-table-column
        ><el-table-column v-if="canEdit" label="状态" width="105"
          ><template #default="{ row }"
            ><el-tag
              :type="row.reviewStatus === 'APPROVED' ? 'success' : 'warning'"
              effect="plain"
              >{{ reviewLabels[row.reviewStatus] }}</el-tag
            ></template
          ></el-table-column
        ><el-table-column label="操作" :width="canEdit ? 230 : 90"
          ><template #default="{ row }"
            ><el-button link @click="favorite(row)">{{
              row.favorite ? '★ 已收藏' : '☆ 收藏'
            }}</el-button
            ><template v-if="canEdit"
              ><el-button link @click="edit(row)">编辑</el-button
              ><el-button link type="primary" @click="openReview(row)">审核</el-button
              ><el-button link type="danger" @click="remove(row)">删除</el-button></template
            ></template
          ></el-table-column
        ></el-table
      >
      <el-pagination
        class="pagination"
        v-model:current-page="page"
        :total="total"
        :page-size="10"
        layout="prev,pager,next,total"
        @current-change="load"
      />
    </section>
    <el-dialog
      v-model="editOpen"
      :title="editingId ? '编辑并重新审核' : '添加题目草稿'"
      width="650px"
      ><el-form label-position="top"
        ><div class="form-grid">
          <el-form-item label="岗位名称"
            ><el-input v-model="form.position" maxlength="100" /></el-form-item
          ><el-form-item label="岗位分类"
            ><el-select v-model="form.jobFamily"
              ><el-option
                v-for="(v, k) in jobFamilies"
                :key="k"
                :value="k"
                :label="v" /></el-select></el-form-item
          ><el-form-item label="难度"
            ><el-select v-model="form.difficulty"
              ><el-option
                v-for="(v, k) in difficultyLabels"
                :key="k"
                :value="k"
                :label="v" /></el-select></el-form-item
          ><el-form-item label="题型"
            ><el-select v-model="form.category"
              ><el-option
                v-for="(v, k) in categoryLabels"
                :key="k"
                :value="k"
                :label="v" /></el-select
          ></el-form-item>
        </div>
        <el-form-item label="知识点标签（逗号分隔，最多 8 个）"
          ><el-input
            v-model="tagText"
            maxlength="240"
            placeholder="Spring，事务，代理" /></el-form-item
        ><el-form-item label="题目内容"
          ><el-input
            v-model="form.content"
            type="textarea"
            :rows="4"
            maxlength="4000" /></el-form-item
        ><el-form-item label="参考答案 / 评分要点"
          ><el-input
            v-model="form.referenceAnswer"
            type="textarea"
            :rows="4"
            maxlength="6000" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="editOpen = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存待审核草稿</el-button
        ></template
      ></el-dialog
    >
    <el-dialog :model-value="!!reviewing" title="题目审核" width="650px" @close="reviewing = null"
      ><template v-if="reviewing"
        ><h3>{{ reviewing.content }}</h3>
        <p class="pre-wrap">{{ reviewing.referenceAnswer }}</p>
        <p class="muted">
          {{ jobFamilies[reviewing.jobFamily] }} ·
          {{ reviewing.tags.split(',').filter(Boolean).join(' / ') }}
        </p>
        <el-input
          v-model="reviewNote"
          type="textarea"
          :rows="3"
          maxlength="500"
          placeholder="审核意见 / 驳回原因"
        />
        <p v-if="reviewing.reviewNote">上次意见：{{ reviewing.reviewNote }}</p></template
      ><template #footer
        ><el-button :loading="saving" type="danger" @click="review('REJECTED')">驳回</el-button
        ><el-button :loading="saving" type="primary" @click="review('APPROVED')"
          >审核通过并发布</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="aiOpen"
      title="AI 出题 · 生成后需审核"
      width="600px"
      :close-on-click-modal="!saving"
      ><el-form label-position="top"
        ><el-form-item label="岗位"
          ><el-input v-model="aiForm.position" maxlength="100" /></el-form-item
        ><el-form-item label="岗位描述"
          ><el-input v-model="aiForm.jobDescription" type="textarea" :rows="4" maxlength="6000"
        /></el-form-item>
        <div class="form-grid">
          <el-form-item label="难度"
            ><el-select v-model="aiForm.difficulty"
              ><el-option
                v-for="(v, k) in difficultyLabels"
                :key="k"
                :value="k"
                :label="v" /></el-select></el-form-item
          ><el-form-item label="题数"
            ><el-input-number v-model="aiForm.count" :min="1" :max="10"
          /></el-form-item></div></el-form
      ><template #footer
        ><el-button type="primary" :loading="saving" @click="generate"
          >生成待审核题目</el-button
        ></template
      ></el-dialog
    >
    <el-dialog :model-value="!!viewing" title="题目详情" width="650px" @close="viewing = null"
      ><template v-if="viewing"
        ><h3>{{ viewing.content }}</h3>
        <p class="muted">{{ viewing.tags.split(',').filter(Boolean).join(' / ') }}</p>
        <p v-if="viewing.referenceAnswer" class="pre-wrap">{{ viewing.referenceAnswer }}</p>
        <p v-else class="muted">完成面试后在报告查看参考要点。</p></template
      ></el-dialog
    >
  </div>
</template>
