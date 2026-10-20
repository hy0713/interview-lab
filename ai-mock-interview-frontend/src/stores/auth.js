import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '../api'
export const useAuth = defineStore('auth', () => {
  const user = ref(null)
  async function load() {
    user.value = await api.get('/auth/me')
  }
  async function authenticate(mode, form) {
    const data = await api.post('/auth/' + mode, form)
    localStorage.setItem('interview-token', data.token)
    user.value = data.user
  }
  async function logout() {
    await api.post('/auth/logout')
    localStorage.removeItem('interview-token')
    user.value = null
  }
  return { user, load, authenticate, logout }
})
