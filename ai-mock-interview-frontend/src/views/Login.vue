<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '../stores/auth'
const auth = useAuth(),
  router = useRouter()
const mode = ref('login'),
  busy = ref(false)
const form = reactive({ username: '', password: '', role: 'CANDIDATE', inviteCode: '' })
async function submit() {
  if (!form.username.trim() || !form.password) return
  busy.value = true
  try {
    await auth.authenticate(mode.value, form)
    router.push('/dashboard')
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="login-page">
    <section class="login-story">
      <div class="brand"><span class="brand-symbol">iL</span>Interview Lab</div>
      <div class="story-body">
        <div class="eyebrow">YOUR NEXT CHAPTER STARTS HERE</div>
        <h1>好机会，<br />留给有准备的你。</h1>
        <p>从一道题开始，把知识变成清晰的表达。<br />用真实的练习，走向你的下一场面试。</p>
        <div class="story-card">
          <div class="story-card-label"><span class="live-dot"></span>一场练习，三个收获</div>
          <div class="story-step"><b>01</b><span>贴近岗位的专业问题</span></div>
          <div class="story-step"><b>02</b><span>独立思考与限时表达</span></div>
          <div class="story-step"><b>03</b><span>具体反馈与成长计划</span></div>
        </div>
      </div>
      <small>INTERVIEW LAB — PRACTICE WITH PURPOSE</small>
    </section>
    <section class="login-form-wrap">
      <form class="login-form" @submit.prevent="submit">
        <div class="eyebrow">WELCOME TO YOUR WORKSPACE</div>
        <h2>{{ mode === 'login' ? '欢迎回来' : '开始你的面试练习' }}</h2>
        <p class="muted">
          {{
            mode === 'login' ? '登录账号，继续为下一个机会做准备。' : '创建账号，记录每一次进步。'
          }}
        </p>
        <div class="mode-tabs">
          <button type="button" :class="{ active: mode === 'login' }" @click="mode = 'login'">
            登录</button
          ><button
            type="button"
            :class="{ active: mode === 'register' }"
            @click="mode = 'register'"
          >
            注册
          </button>
        </div>
        <label>用户名</label
        ><el-input
          v-model="form.username"
          size="large"
          placeholder="请输入用户名"
          maxlength="32"
          autocomplete="username"
          required
        />
        <label>密码</label
        ><el-input
          v-model="form.password"
          size="large"
          type="password"
          show-password
          placeholder="请输入密码"
          maxlength="64"
          :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
          required
        />
        <template v-if="mode === 'register'"
          ><label>账号角色</label
          ><el-radio-group v-model="form.role"
            ><el-radio-button value="CANDIDATE" label="CANDIDATE">候选人</el-radio-button
            ><el-radio-button value="INTERVIEWER" label="INTERVIEWER"
              >面试官</el-radio-button
            ></el-radio-group
          ><template v-if="form.role === 'INTERVIEWER'"
            ><label>面试官邀请码</label
            ><el-input v-model="form.inviteCode" size="large" placeholder="请输入邀请码"
          /></template>
          <p class="form-hint">用户名 3～32 位字母、数字或下划线；密码 6～64 位。</p></template
        >
        <el-button
          type="primary"
          size="large"
          native-type="submit"
          :loading="busy"
          class="full-width"
          >{{ mode === 'login' ? '进入练习室' : '创建账号' }} →</el-button
        >
        <p class="login-footer">每一次认真练习，都有意义。</p>
      </form>
    </section>
  </div>
</template>
