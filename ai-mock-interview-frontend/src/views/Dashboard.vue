<script setup>
import { ref, onMounted } from 'vue'
import { useAuth } from '../stores/auth'
import api from '../api'
import CreateInterview from '../components/CreateInterview.vue'
import SystemStatus from '../components/SystemStatus.vue'
import { date, statusLabels, statusType } from '../labels'
const auth = useAuth(),
  stats = ref({}),
  latest = ref([]),
  createOpen = ref(false),
  loading = ref(true)
async function load() {
  loading.value = true
  try {
    ;[stats.value, latest.value] = await Promise.all([
      api.get('/interviews/stats'),
      api.get('/interviews', { params: { size: 5 } }).then((p) => p.records)
    ])
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
<template>
  <div v-loading="loading">
    <div class="page-heading">
      <div>
        <div class="eyebrow">YOUR PRACTICE, YOUR PROGRESS</div>
        <h1>你好，{{ auth.user?.username }} <span class="greeting-dot">.</span></h1>
        <p>为下一场面试，多积累一点从容。</p>
      </div>
      <el-button type="primary" size="large" @click="createOpen = true">＋ 新建面试</el-button>
    </div>
    <section class="hero-panel">
      <div>
        <span class="pill">练习，让表达更有力量</span>
        <h2>把「我知道」，<br />变成「我讲得清楚」。</h2>
        <p>选择目标岗位，完成一场专注的模拟面试。<br />在限时思考与具体反馈中，找到你的下一步。</p>
        <el-button size="large" @click="createOpen = true">开始一次练习 ↗</el-button>
      </div>
      <div class="hero-art" aria-hidden="true">
        <div class="orbit orbit-one"></div>
        <div class="orbit orbit-two"></div>
        <div class="art-card">
          <span>THE NEXT STEP</span><b>准备好了<br />就向前一步。</b>
          <div class="art-bars"><i></i><i></i><i></i><i></i><i></i></div>
          <small>Small steps. Real progress.</small>
        </div>
        <div class="art-spark">✳</div>
      </div>
    </section>
    <div class="stats-grid">
      <div class="stat-card">
        <span>累计面试</span><strong>{{ stats.total || 0 }}<small>场</small></strong>
        <p>每次练习，都是一次积累</p>
      </div>
      <div class="stat-card">
        <span>进行中 / 评估中</span><strong>{{ stats.active || 0 }}<small>场</small></strong>
        <p>保持专注，完成当前练习</p>
      </div>
      <div class="stat-card">
        <span>已完成面试</span><strong>{{ stats.completed || 0 }}<small>场</small></strong>
        <p>用复盘连接下一次进步</p>
      </div>
      <div class="stat-card">
        <span>题库储备</span><strong>{{ stats.questionCount || 0 }}<small>题</small></strong>
        <p>覆盖专业知识与实践表达</p>
      </div>
    </div>
    <div v-if="stats.aiConfigured === false" class="soft-note mb">
      AI 出题与评分暂未启用。你可以先创建题库练习；启用 AI 后可重试评分。
    </div>
    <SystemStatus v-if="auth.user?.role === 'INTERVIEWER'" />
    <section class="panel">
      <div class="section-heading">
        <h3>最近的面试</h3>
        <router-link to="/interviews">查看全部 →</router-link>
      </div>
      <el-table :data="latest"
        ><el-table-column prop="position" label="面试岗位" min-width="160" /><el-table-column
          label="状态"
          width="130"
          ><template #default="{ row }"
            ><el-tag :type="statusType(row.status)" effect="plain">{{
              statusLabels[row.status]
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="创建时间" min-width="160"
          ><template #default="{ row }">{{ date(row.createdAt) }}</template></el-table-column
        ><el-table-column label="得分" width="90"
          ><template #default="{ row }">{{ row.totalScore ?? '—' }}</template></el-table-column
        ><el-table-column width="120"
          ><template #default="{ row }"
            ><router-link :to="'/interviews/' + row.id"
              >{{ row.status === 'COMPLETED' ? '查看报告' : '进入面试' }} →</router-link
            ></template
          ></el-table-column
        ><template #empty
          ><div class="empty-inline">还没有面试记录，开始你的第一场练习吧。</div></template
        ></el-table
      >
    </section>
    <CreateInterview v-model="createOpen" @created="load" />
  </div>
</template>
