import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig(({ mode }) => ({
  plugins: [vue()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: `http://127.0.0.1:${loadEnv(mode, '..', 'BACKEND_').BACKEND_PORT || 8080}`,
        changeOrigin: true
      }
    }
  }
}))
