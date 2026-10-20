<script setup>
import { useAuth } from './stores/auth'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { House, Collection, ChatLineSquare, Document, SwitchButton } from '@element-plus/icons-vue'
const auth = useAuth(),
  route = useRoute(),
  router = useRouter()
const links = computed(() => [
  ...(auth.user?.role === 'INTERVIEWER'
    ? [{ path: '/management', label: '面试官工作台', icon: House }]
    : [
        { path: '/dashboard', label: '练习概览', icon: House },
        { path: '/learning', label: '成长与复练', icon: Collection }
      ]),
  { path: '/interviews', label: '面试记录', icon: ChatLineSquare },
  { path: '/questions', label: '面试题库', icon: Collection },
  { path: '/reports', label: '评估报告', icon: Document }
])
async function logout() {
  await auth.logout()
  router.push('/login')
}
</script>
<template>
  <div class="app-shell">
    <aside class="sidebar">
      <router-link to="/dashboard" class="brand"
        ><span class="brand-symbol">iL</span
        ><span>Interview<span class="brand-sub">LAB / 面试练习室</span></span></router-link
      >
      <div class="sidebar-label">WORKSPACE</div>
      <nav>
        <router-link
          v-for="link in links"
          :key="link.path"
          :to="link.path"
          :class="{ selected: route.path.startsWith(link.path) }"
          ><el-icon><component :is="link.icon" /></el-icon>{{ link.label }}</router-link
        >
      </nav>
      <div class="sidebar-note">
        <span class="live-dot"></span>把每一次练习<br />变成下一次的底气。
        <div class="note-small">准备 · 表达 · 复盘 · 成长</div>
      </div>
      <div class="account">
        <span class="avatar">{{ auth.user?.username?.slice(0, 1).toUpperCase() }}</span>
        <div>
          <b>{{ auth.user?.username }}</b
          ><small>{{ auth.user?.role === 'INTERVIEWER' ? '面试官' : '候选人' }}</small>
        </div>
        <el-button text class="logout" aria-label="退出登录" @click="logout"
          ><el-icon><SwitchButton /></el-icon
        ></el-button>
      </div>
    </aside>
    <main class="workspace">
      <header class="topbar">
        <span>工作空间 <span class="slash">/</span> {{ route.meta.title }}</span
        ><span class="topbar-badge">AI ASSISTED PRACTICE</span>
      </header>
      <div class="page"><router-view :key="$route.path" /></div>
    </main>
  </div>
</template>
