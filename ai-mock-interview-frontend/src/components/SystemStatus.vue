<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'
const system = ref(null),
  checkingAi = ref(false),
  aiMessage = ref('')
async function load() {
  try {
    system.value = await api.get('/system/status', { silent: true })
  } catch {
    aiMessage.value = '服务状态暂未加载，可重试检查'
  }
}
onMounted(load)
async function checkAi() {
  checkingAi.value = true
  try {
    const result = await api.post('/system/ai-check')
    aiMessage.value = result.message
  } finally {
    checkingAi.value = false
  }
}
</script>
<template>
  <section class="panel mb">
    <div class="section-heading">
      <h3>运行状态</h3>
      <el-button :loading="checkingAi" @click="checkAi">检查 AI 连通性</el-button>
    </div>
    <el-button v-if="!system" @click="load">重新加载服务状态</el-button>
    <div v-if="system" class="dependency-grid">
      <span
        v-for="(label, key) in { database: 'MySQL', redis: 'Redis', rabbitmq: 'RabbitMQ' }"
        :key="key"
        class="dependency-item"
        >{{ label }}
        <el-tag :type="system[key] ? 'success' : 'danger'" effect="plain">{{
          system[key] ? '可用' : '连接异常'
        }}</el-tag></span
      ><span class="dependency-item"
        >AI Key：{{ system.aiConfigured ? '已配置（尚需连通性检查）' : '未配置' }}</span
      >
    </div>
    <p v-if="aiMessage" class="ai-status-message">{{ aiMessage }}</p>
  </section>
</template>
