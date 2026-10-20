import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from './stores/auth'
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('./views/Login.vue') },
    {
      path: '/',
      component: () => import('./Layout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          component: () => import('./views/Dashboard.vue'),
          meta: { title: '练习概览' }
        },
        {
          path: 'questions',
          component: () => import('./views/Questions.vue'),
          meta: { title: '面试题库' }
        },
        {
          path: 'interviews',
          component: () => import('./views/Interviews.vue'),
          meta: { title: '面试记录' }
        },
        {
          path: 'interviews/:id',
          component: () => import('./views/Interview.vue'),
          meta: { title: '面试练习室' }
        },
        {
          path: 'reports',
          component: () => import('./views/Reports.vue'),
          meta: { title: '评估报告' }
        },
        {
          path: 'reports/:id',
          component: () => import('./views/Report.vue'),
          meta: { title: '报告复盘' }
        },
        {
          path: 'learning',
          component: () => import('./views/Learning.vue'),
          meta: { title: '成长与复练' }
        },
        {
          path: 'management',
          component: () => import('./views/Management.vue'),
          meta: { title: '面试官工作台', interviewer: true }
        }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})
router.beforeEach(async (to) => {
  document.title = (to.meta.title || '登录') + ' · Interview Lab'
  const token = localStorage.getItem('interview-token')
  if (!token) return to.path === '/login' ? true : '/login'
  const auth = useAuth()
  if (!auth.user) {
    try {
      await auth.load()
    } catch {
      return to.path === '/login' ? true : '/login'
    }
  }
  if (to.meta.interviewer && auth.user?.role !== 'INTERVIEWER') return '/dashboard'
  if (to.path === '/dashboard' && auth.user?.role === 'INTERVIEWER') return '/management'
  if (to.path === '/login') return auth.user?.role === 'INTERVIEWER' ? '/management' : '/dashboard'
})
export default router
