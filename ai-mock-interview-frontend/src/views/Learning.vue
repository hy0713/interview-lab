<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'
import ScoreCharts from '../components/ScoreCharts.vue'
import CreateInterview from '../components/CreateInterview.vue'
import { difficultyLabels } from '../labels'
const router = useRouter(),
  overview = ref({}),
  rows = ref([]),
  total = ref(0),
  page = ref(1),
  tag = ref(''),
  keyword = ref(''),
  position = ref(''),
  difficulty = ref(''),
  createOpen = ref(false),
  focusTag = ref(''),
  loading = ref(false),
  error = ref(false),
  busy = ref(null)
async function load() {
  loading.value = true
  try {
    const [o, m] = await Promise.all([
      api.get('/learning/overview', {
        params: { position: position.value, difficulty: difficulty.value }
      }),
      api.get('/learning/mistakes', {
        params: { page: page.value, size: 8, tag: tag.value, keyword: keyword.value }
      })
    ])
    overview.value = o
    rows.value = m.records
    total.value = m.total
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
function focus(value) {
  focusTag.value = value
  createOpen.value = true
}
async function practice(row) {
  busy.value = row.id
  try {
    const result = await api.post('/learning/turns/' + row.id + '/practice')
    router.push('/interviews/' + result.id)
  } finally {
    busy.value = null
  }
}
onMounted(load)
</script>
<template>
  <div v-loading="loading">
    <div class="page-heading">
      <div>
        <div class="eyebrow">YOUR LEARNING LOOP</div>
        <h1>把薄弱点，练成下一次的底气。</h1>
        <p>从错题出发，专项练习，再回到同题对比。</p>
      </div>
    </div>
    <el-alert v-if="error" title="加载失败，可重试" type="warning" class="mb"
      ><el-button @click="load">重新加载</el-button></el-alert
    >
    <section class="panel mb">
      <div class="filter-bar">
        <el-input
          v-model="position"
          placeholder="成绩趋势：精确岗位名称"
          clearable
          style="max-width: 260px"
        /><el-select placeholder="全部难度" v-model="difficulty" style="width: 150px"
          ><el-option label="全部难度" value="" /><el-option
            v-for="(v, k) in difficultyLabels"
            :key="k"
            :value="k"
            :label="v" /></el-select
        ><el-button @click="load">更新图表</el-button>
      </div>
    </section>
    <ScoreCharts :trend="overview.trend" :abilities="overview.abilities" />
    <section class="panel mb">
      <h3>知识点表现 · 最近 30 场已完成面试</h3>
      <div class="knowledge-grid">
        <article v-for="item in overview.weakTags" :key="item.name" class="knowledge-card">
          <h4>{{ item.name }}</h4>
          <b>{{ item.score }} 分</b>
          <p>{{ item.samples }} 次作答</p>
          <el-button v-if="item.name !== '未分类'" @click="focus(item.name)"
            >知识点专项练习 →</el-button
          >
          <p v-else class="muted">早期回答尚未记录知识点</p>
        </article>
      </div>
      <el-empty
        v-if="!overview.weakTags?.length"
        description="完成面试后显示知识点表现"
        :image-size="60"
      />
    </section>
    <section class="panel">
      <div class="section-heading">
        <h3>错题复练 <small class="muted">已完成面试中低于 75 分的回答</small></h3>
        <span>{{ total }} 次回答</span>
      </div>
      <div class="filter-bar">
        <el-input
          v-model="keyword"
          placeholder="搜索问题或岗位"
          clearable
          style="max-width: 300px"
          @keyup.enter="search"
        /><el-input
          v-model="tag"
          placeholder="知识点精确筛选"
          clearable
          style="max-width: 200px"
        /><el-button @click="search">搜索</el-button>
      </div>
      <el-table :data="rows"
        ><el-table-column prop="content" label="题目" min-width="300" /><el-table-column
          prop="position"
          label="岗位"
          min-width="120"
        /><el-table-column prop="score" label="历史得分" width="90" /><el-table-column
          label="操作"
          width="220"
          ><template #default="{ row }"
            ><el-button link type="primary" :loading="busy === row.id" @click="practice(row)"
              >同题重答</el-button
            ><router-link :to="'/reports/' + row.interview_id">原报告 →</router-link></template
          ></el-table-column
        ></el-table
      >
      <el-pagination
        class="pagination"
        v-model:current-page="page"
        :total="total"
        :page-size="8"
        layout="prev,pager,next,total"
        @current-change="load"
      />
    </section>
    <CreateInterview v-model="createOpen" :initial-tag="focusTag" />
  </div>
</template>
