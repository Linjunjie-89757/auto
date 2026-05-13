export type ReviewStatus = 'PENDING' | 'PASSED' | 'REJECTED'
export type ExecutionStatus = 'NOT_RUN' | 'PASSED' | 'BLOCKED' | 'FAILED'

export function reviewStatusTagType(status: string) {
  if (status === 'PASSED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}

export function reviewStatusTagClass(status: string) {
  if (status === 'PASSED') return 'status-tag-passed'
  if (status === 'REJECTED') return 'status-tag-failed'
  return 'status-tag-pending'
}

export function reviewStatusLabel(status: string) {
  if (status === 'PASSED') return '已通过'
  if (status === 'REJECTED') return '不通过'
  return '未评审'
}

export function executionStatusTagType(status: string) {
  if (status === 'PASSED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'BLOCKED') return 'primary'
  return 'info'
}

export function executionStatusTagClass(status: string) {
  if (status === 'PASSED') return 'status-tag-passed'
  if (status === 'FAILED') return 'status-tag-failed'
  if (status === 'BLOCKED') return 'status-tag-blocked'
  return 'status-tag-pending'
}

export function executionStatusLabel(status: string) {
  if (status === 'PASSED') return '已通过'
  if (status === 'FAILED') return '失败'
  if (status === 'BLOCKED') return '阻塞中'
  return '未执行'
}

export function formatDateTime(value: string | null) {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 16)
}

export function normalizeReviewStatus(status: string): ReviewStatus {
  if (status === 'PASSED' || status === 'REJECTED') {
    return status
  }
  return 'PENDING'
}
