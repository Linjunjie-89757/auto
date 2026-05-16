export const bugStatusLabelMap: Record<string, string> = {
  TODO: '待处理',
  ASSIGNED: '已指派',
  IN_PROGRESS: '处理中',
  PENDING_VERIFY: '待验证',
  CLOSED: '已关闭',
  REJECTED: '已拒绝',
}

export const bugSeverityLabelMap: Record<string, string> = {
  CRITICAL: '致命',
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
}

export const bugSourceTypeLabelMap: Record<string, string> = {
  MANUAL: '手工',
  CASE: '用例',
  REPORT: '报告',
}

export function formatBugStatus(value: string | null | undefined) {
  if (!value) {
    return '-'
  }
  return bugStatusLabelMap[value] ?? value
}

export function formatBugSeverity(value: string | null | undefined) {
  if (!value) {
    return '-'
  }
  return bugSeverityLabelMap[value] ?? value
}

export function formatBugSourceType(value: string | null | undefined) {
  if (!value) {
    return '-'
  }
  return bugSourceTypeLabelMap[value] ?? value
}

export function formatBugDateTime(value: string | null | undefined) {
  if (!value) {
    return '-'
  }
  return value.slice(0, 16).replace('T', ' ')
}

export function isImageFile(contentType?: string | null, fileName?: string | null) {
  if (contentType?.startsWith('image/')) {
    return true
  }
  return !!fileName && /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(fileName)
}
