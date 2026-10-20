export const statusLabels = {
  PAUSED: '已暂停',
  CANCELLED: '已取消',
  CREATED: '待开始',
  IN_PROGRESS: '面试中',
  EVALUATING: '评估中',
  COMPLETED: '已完成',
  WAITING: '待作答',
  ACTIVE: '作答中',
  ANSWERED: '已提交',
  TIMEOUT: '超时',
  SKIPPED: '未作答',
  NONE: '未开始',
  PENDING: '排队中',
  PROCESSING: 'AI 处理中',
  SUCCEEDED: '已完成',
  FAILED: '处理失败'
}
export const difficultyLabels = { EASY: '基础', MEDIUM: '进阶', HARD: '挑战' }
export const categoryLabels = {
  TECHNICAL: '专业知识',
  BEHAVIORAL: '行为面试',
  SCENARIO: '情景分析'
}
export function statusType(status) {
  return (
    {
      COMPLETED: 'success',
      SUCCEEDED: 'success',
      FAILED: 'danger',
      IN_PROGRESS: 'warning',
      EVALUATING: 'warning',
      TIMEOUT: 'danger'
    }[status] || 'info'
  )
}
export function date(value) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

export const jobFamilies = {
  JAVA_BACKEND: 'Java 后端',
  FRONTEND: '前端开发',
  TESTING: '软件测试',
  GENERAL: '通用岗位'
}
export const reviewLabels = {
  DRAFT: '草稿',
  PENDING: 'AI 待审核',
  APPROVED: '已发布',
  REJECTED: '已驳回'
}
