import fs from 'node:fs'

const path = 'D:/CodeProject/auto/web/src/components/BugDetailDrawer.vue'
let source = fs.readFileSync(path, 'utf8')

const oldBlock = /const tabItems: Array<\{ key: DrawerTab, label: string \}> = \[[\s\S]*?const availableUsers = computed\(\(\) => \{[\s\S]*?\n\}\)\n/
const newBlock = `const tabItems: Array<{ key: DrawerTab, label: string }> = [
  { key: 'basic', label: '基本信息' },
  { key: 'detail', label: '详情' },
  { key: 'case', label: '关联' },
  { key: 'comment', label: '评论' },
  { key: 'history', label: '变更历史' },
]

const detail = computed<BugDetail | null>(() => {
  if (!props.detail) {
    return null
  }
  return {
    ...props.detail,
    tags: props.detail.tags ?? [],
    attachments: props.detail.attachments ?? [],
    sourceContext: props.detail.sourceContext ?? {
      sourceType: props.detail.sourceType ?? '',
      caseSummary: null,
      reportSummary: null,
      taskSummary: null,
    },
    activities: props.detail.activities ?? [],
    flows: props.detail.flows ?? [],
    comments: props.detail.comments ?? [],
  }
})

const selectableStatusOptions = computed(() => {
  if (!detail.value) {
    return statusOptions
  }
  const currentStatus = detail.value.status
  return statusOptions.filter(item => item.value !== currentStatus)
})

const imageAttachments = computed(() => (
  detail.value?.attachments.filter(item => isImageFile(item.contentType, item.fileName)) ?? []
))

const fileAttachments = computed(() => (
  detail.value?.attachments.filter(item => !isImageFile(item.contentType, item.fileName)) ?? []
))

const previewUrls = computed(() => imageAttachments.value.map(item => resolveApiUrl(item.downloadUrl || '')))
const descriptionHtml = computed(() => sanitizeRichHtml(detail.value?.description || ''))
const latestFlow = computed(() => detail.value?.flows[detail.value.flows.length - 1] || null)
const headerBugNo = computed(() => detail.value?.bugNo || props.summary?.bugNo || '-')
const headerTitle = computed(() => detail.value?.title || props.summary?.title || '未命名缺���')
const headerStatus = computed(() => detail.value?.status || props.summary?.status || '')
const availableUsers = computed(() => {
  if (!detail.value) {
    return props.users
  }
  const workspaceCode = detail.value.workspaceCode
  return props.users.filter(item => item.workspaceCodes.includes(workspaceCode))
})
`
source = source.replace(oldBlock, newBlock)

const replacements = [
  ["watch(() => props.detail?.id, () => {", "watch(() => detail.value?.id, () => {"],
  ["  selectedAssigneeId.value = props.detail?.assigneeId ?? null", "  selectedAssigneeId.value = detail.value?.assigneeId ?? null"],
  ["watch(() => props.detail?.assigneeId, (value) => {", "watch(() => detail.value?.assigneeId, (value) => {"],
  ["  if (!props.detail) {", "  if (!detail.value) {"],
  ["    path: `/bugs/${props.detail.id}`,", "    path: `/bugs/${detail.value.id}`,"],
  ["    query: { workspace: props.detail.workspaceCode },", "    query: { workspace: detail.value.workspaceCode },"],
  ["    `缺陷编号�?{props.detail.bugNo}`", "    `缺陷编号�?{detail.value.bugNo}`"],
  ["    `缺陷标题�?{props.detail.title}`", "    `缺陷标题�?{detail.value.title}`"],
  ["    `状态：${formatBugStatus(props.detail.status)}`", "    `状态：${formatBugStatus(detail.value.status)}`"],
  ["    `优先级：${props.detail.priority}`", "    `优先级：${detail.value.priority}`"],
  ["    `严重程度�?{formatBugSeverity(props.detail.severity)}`", "    `严重程度�?{formatBugSeverity(detail.value.severity)}`"],
  ["    `负责人：${props.detail.assigneeName || '-'}`", "    `负责人：${detail.value.assigneeName || '-'}`"],
  ["  if (selectedAssigneeId.value === (props.detail.assigneeId ?? null)) {", "  if (selectedAssigneeId.value === (detail.value.assigneeId ?? null)) {"],
  ["    query: { workspace: props.detail.workspaceCode, reportId: String(id) },", "    query: { workspace: detail.value.workspaceCode, reportId: String(id) },"],
  ["    query: { workspace: props.detail.workspaceCode, taskId: String(id) },", "    query: { workspace: detail.value.workspaceCode, taskId: String(id) },"],
]
for (const [from, to] of replacements) {
  source = source.replace(from, to)
}

fs.writeFileSync(path, source)
