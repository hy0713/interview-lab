<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'
import { difficultyLabels, date } from '../labels'
const rows = ref([]),
  total = ref(0),
  page = ref(1),
  keyword = ref(''),
  difficulty = ref(''),
  status = ref('COMPLETED'),
  loading = ref(false),
  error = ref(false)
async function load() {
  loading.value = true
  try {
    const result = await api.get('/interviews', {
      params: {
        page: page.value,
        size: 9,
        status: status.value,
        keyword: keyword.value,
        difficulty: difficulty.value
      }
    })
    rows.value = result.records
    total.value = result.total
    error.value = false
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}
function search() {
  page.value = 1
  load()
}
onMounted(load)
</script>
<template>
  <div v-loading="loading">
    <div class="page-heading">
      <div>
        <div class="eyebrow">YOUR REVIEW LIBRARY</div>
        <h1>每一次复盘，都留下下一步。</h1>
        <p>查看评分依据、人工评语与同题进步。</p>
      </div>
    </div>
    <section class="panel mb">
      <div class="filter-bar">
        <el-input
          v-model="keyword"
          placeholder="搜索岗位"
          clearable
          style="max-width: 280px"
          @keyup.enter="search"
        /><el-select placeholder="全部难度" v-model="difficulty" style="width: 130px"
          ><el-option label="全部难度" value="" /><el-option
            v-for="(v, k) in difficultyLabels"
            :key="k"
            :value="k"
            :label="v" /></el-select
        ><el-select v-model="status" style="width: 150px"
          ><el-option label="已完成报告" value="COMPLETED" /><el-option
            label="生成中 / 需重试"
            value="EVALUATING" /></el-select
        ><el-button @click="search">筛选</el-button>
      </div>
    </section>
    <el-alert v-if="error" title="报告列表加载失败" type="warning"
      ><el-button @click="load">重新加载</el-button></el-alert
    >
    <div class="knowledge-grid">
      <router-link
        v-for="row in rows"
        :key="row.id"
        class="panel report-card"
        :to="'/reports/' + row.id"
        ><small>#{{ row.id }} · {{ date(row.createdAt) }}</small>
        <h3>{{ row.position }}</h3>
        <strong>{{ row.totalScore ?? '…' }} <small>/ 100</small></strong>
        <p>
          {{ difficultyLabels[row.difficulty] }} ·
          {{ row.status === 'COMPLETED' ? '已生成完整报告' : '查看任务进度与恢复入口' }}
        </p>
        <span>查看复盘 →</span></router-link
      >
    </div>
    <el-empty v-if="!loading && !rows.length" description="暂无匹配报告" />
    <el-pagination
      class="pagination"
      v-model:current-page="page"
      :total="total"
      :page-size="9"
      layout="prev,pager,next,total"
      @current-change="load"
    />
  </div>
</template>
