<script setup>
import { ref, onMounted, watch, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAuth } from '../stores/auth'
import api from '../api'
import CreateInterview from '../components/CreateInterview.vue'
import { date, statusLabels, statusType, difficultyLabels } from '../labels'
const route = useRoute(),
  auth = useAuth()
const rows = ref([]),
  total = ref(0),
  page = ref(1),
  status = ref(''),
  loading = ref(false),
  createOpen = ref(false)
async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const data = await api.get('/interviews', {
      params: { page: page.value, size: 10, status: status.value },
      silent
    })
    rows.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function reset() {
  page.value = 1
  load()
}
watch(
  () => route.meta.reportMode,
  () => {
    status.value = route.meta.reportMode ? 'COMPLETED' : ''
    reset()
  }
)
let timer
onMounted(() => {
  status.value = route.meta.reportMode ? 'COMPLETED' : ''
  load()
  timer = setInterval(() => {
    if (rows.value.some((r) => r.status === 'EVALUATING')) load(true).catch(() => {})
  }, 5000)
})
onUnmounted(() => clearInterval(timer))
</script>
<template>
  <div class="page-heading">
    <div>
      <div class="eyebrow">PRACTICE JOURNAL</div>
      <h1>{{ route.meta.reportMode ? '你的面试，值得复盘。' : '每一场练习，都算数。' }}</h1>
      <p>
        {{
          auth.user?.role === 'INTERVIEWER'
            ? '查看所有候选人的面试进度与评估结果。'
            : '记录你的表达，找到持续进步的方向。'
        }}
      </p>
    </div>
    <el-button type="primary" size="large" @click="createOpen = true">＋ 新建面试</el-button>
  </div>
  <section class="panel">
    <div class="section-heading">
      <h3>
        {{ route.meta.reportMode ? '评估报告' : '面试记录' }}
        <span class="count-label">{{ total }}</span>
      </h3>
      <el-select v-model="status" style="width: 150px" @change="reset"
        ><el-option label="全部状态" value="" /><el-option
          v-for="s in ['CREATED', 'IN_PROGRESS', 'PAUSED', 'EVALUATING', 'COMPLETED', 'CANCELLED']"
          :key="s"
          :label="statusLabels[s]"
          :value="s"
      /></el-select>
    </div>
    <el-table v-loading="loading" :data="rows"
      ><el-table-column label="面试" min-width="190"
        ><template #default="{ row }"
          ><div class="table-title">{{ row.position }}</div>
          <small class="muted"
            >#{{ row.id }} · {{ row.source === 'AI' ? 'AI 定制' : '题库练习' }}</small
          ></template
        ></el-table-column
      ><el-table-column
        v-if="auth.user?.role === 'INTERVIEWER'"
        prop="candidateId"
        label="候选人 ID"
        width="110"
      /><el-table-column label="难度" width="90"
        ><template #default="{ row }">{{
          difficultyLabels[row.difficulty]
        }}</template></el-table-column
      ><el-table-column label="状态" width="120"
        ><template #default="{ row }"
          ><el-tag :type="statusType(row.status)" effect="plain">{{
            statusLabels[row.status]
          }}</el-tag></template
        ></el-table-column
      ><el-table-column label="创建时间" min-width="160"
        ><template #default="{ row }">{{ date(row.createdAt) }}</template></el-table-column
      ><el-table-column label="总分" width="85"
        ><template #default="{ row }"
          ><b>{{ row.totalScore ?? '—' }}</b></template
        ></el-table-column
      ><el-table-column width="120"
        ><template #default="{ row }"
          ><router-link :to="(row.status === 'COMPLETED' ? '/reports/' : '/interviews/') + row.id"
            >{{ row.status === 'COMPLETED' ? '查看报告' : '进入详情' }} →</router-link
          ></template
        ></el-table-column
      ><template #empty
        ><el-empty description="暂无面试记录"
          ><el-button type="primary" @click="createOpen = true">开始第一次练习</el-button></el-empty
        ></template
      ></el-table
    >
    <el-pagination
      class="pagination"
      v-model:current-page="page"
      :total="total"
      :page-size="10"
      layout="prev, pager, next, total"
      @current-change="load()"
    />
  </section>
  <CreateInterview v-model="createOpen" @created="load()" />
</template>
