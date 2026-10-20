<script setup>
import { ref, reactive, onMounted } from 'vue'
import api from '../api'
import CreateInterview from '../components/CreateInterview.vue'
import SystemStatus from '../components/SystemStatus.vue'
import { statusLabels, statusType, date } from '../labels'
const data = ref({}),
  candidates = ref([]),
  loading = ref(false),
  error = ref(false),
  open = ref(false),
  page = ref(1)
const filters = reactive({ keyword: '', status: '', candidateId: null, minScore: null })
async function load() {
  loading.value = true
  try {
    data.value = await api.get('/management/workbench', {
      params: { ...filters, page: page.value, size: 10 }
    })
    error.value = false
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}
async function findCandidates(keyword = '') {
  candidates.value = (
    await api.get('/management/candidates', { params: { keyword, size: 50 } })
  ).records
}
function search() {
  page.value = 1
  load()
}
onMounted(() => {
  load()
  findCandidates()
})
</script>
<template>
  <div v-loading="loading">
    <div class="page-heading">
      <div>
        <div class="eyebrow">INTERVIEWER WORKSPACE</div>
        <h1>让每一次面试，都有可跟进的结果。</h1>
        <p>指派候选人、查看进度、复核报告与维护题库。</p>
      </div>
      <el-button type="primary" @click="open = true">＋ 指派面试</el-button>
    </div>
    <div class="stats-grid">
      <div class="stat-card">
        <span>匹配面试</span><strong>{{ data.total || 0 }}</strong>
      </div>
      <div class="stat-card">
        <span>已完成待人工复核</span><strong>{{ data.awaitingReview || 0 }}</strong>
      </div>
      <div class="stat-card">
        <span>待处理题目</span><strong>{{ data.pendingQuestions || 0 }}</strong
        ><router-link to="/questions">进入题目审核 →</router-link>
      </div>
      <div class="stat-card">
        <span>候选人</span>
        <p>按用户名搜索并在下方筛选</p>
        <el-button @click="findCandidates(filters.keyword)">刷新候选人</el-button>
      </div>
    </div>
    <el-alert v-if="error" title="工作台加载失败" type="warning" class="mb"
      ><el-button @click="load">重新加载</el-button></el-alert
    >
    <section class="panel">
      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="候选人用户名 / 岗位"
          style="max-width: 240px"
          @keyup.enter="search"
        />
        <el-select
          v-model="filters.candidateId"
          filterable
          remote
          :remote-method="findCandidates"
          clearable
          placeholder="候选人筛选"
          style="width: 180px"
          ><el-option v-for="u in candidates" :key="u.id" :value="u.id" :label="u.username"
        /></el-select>
        <el-select placeholder="全部状态" v-model="filters.status" style="width: 140px"
          ><el-option label="全部状态" value="" /><el-option
            v-for="(v, k) in statusLabels"
            :key="k"
            :value="k"
            :label="v"
        /></el-select>
        <el-input-number
          v-model="filters.minScore"
          :min="0"
          :max="100"
          placeholder="最低 AI 分"
        /><el-button @click="search">筛选</el-button>
      </div>
      <el-table :data="data.records"
        ><el-table-column prop="candidate_name" label="候选人" width="105" /><el-table-column
          prop="position"
          label="岗位"
          min-width="120"
        /><el-table-column label="状态" width="105"
          ><template #default="{ row }"
            ><el-tag :type="statusType(row.status)" effect="plain">{{
              statusLabels[row.status]
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="评分进度" width="120"
          ><template #default="{ row }"
            ><el-progress
              :percentage="Math.round((row.scored_count / Math.max(1, row.turn_count)) * 100)"
              :show-text="false"
            /><small>{{ row.scored_count }} / {{ row.turn_count }} 轮</small></template
          ></el-table-column
        ><el-table-column prop="total_score" label="AI 分" width="65" /><el-table-column
          prop="human_score"
          label="人工分"
          width="65"
        /><el-table-column label="创建时间" min-width="120"
          ><template #default="{ row }">{{ date(row.created_at) }}</template></el-table-column
        ><el-table-column label="操作" width="105"
          ><template #default="{ row }"
            ><router-link :to="(row.status === 'COMPLETED' ? '/reports/' : '/interviews/') + row.id"
              >{{ row.status === 'COMPLETED' ? '复核报告' : '查看进度' }} →</router-link
            ></template
          ></el-table-column
        ></el-table
      >
      <el-pagination
        class="pagination"
        v-model:current-page="page"
        :total="data.total || 0"
        :page-size="10"
        layout="prev,pager,next,total"
        @current-change="load"
      />
    </section>
    <SystemStatus />
    <CreateInterview v-model="open" @created="load" />
  </div>
</template>
