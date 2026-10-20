import axios from 'axios'
import { ElMessage } from 'element-plus'
const api = axios.create({ baseURL: '/api', timeout: 180000 })
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('interview-token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})
api.interceptors.response.use(
  (response) => response.data.data,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('interview-token')
      if (location.pathname !== '/login') location.href = '/login'
    }
    if (!error.config?.silent)
      ElMessage.error(error.response?.data?.message || '请求失败，请检查网络或稍后重试')
    return Promise.reject(error)
  }
)
export default api
