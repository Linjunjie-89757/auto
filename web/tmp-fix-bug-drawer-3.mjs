import fs from 'node:fs'
const path = 'D:/CodeProject/auto/web/src/components/BugDetailDrawer.vue'
let source = fs.readFileSync(path, 'utf8')

source = source.replace(/const headerTitle = computed\([^\n]*\n/, "const headerTitle = computed(() => detail.value?.title || props.summary?.title || '-')\n")
source = source.replace(/async function copyBugSummary\(\) \{[\s\S]*?\n\}\n\nfunction showDeleteUnavailable/, `async function copyBugSummary() {
  if (!detail.value) {
    return
  }
  const summary = [
    \\`缺陷编号：\\${detail.value.bugNo}\\`,
    \\`缺陷标题：\\${detail.value.title}\\`,
    \\`状态：\\${formatBugStatus(detail.value.status)}\\`,
    \\`优先级：\\${detail.value.priority}\\`,
    \\`严重程度：\\${formatBugSeverity(detail.value.severity)}\\`,
    \\`负责人：\\${detail.value.assigneeName || '-'}\\`,
  ].join('\\n')

  try {
    await navigator.clipboard.writeText(summary)
    ElMessage.success('缂洪櫡淇℃伅宸插鍒?')
  }
  catch {
    ElMessage.error('缂洪櫡淇℃伅澶嶅埗澶辫触')
  }
}

function showDeleteUnavailable`)
source = source.replace(/function openCase\(id: number\) \{[\s\S]*?\n\}\n\nfunction openReport/, `function openCase(id: number) {
  if (!detail.value) {
    return
  }
  router.push({
    path: \\`/cases/manage/execute/\\${id}\\`,
    query: { workspace: detail.value.workspaceCode },
  })
}

function openReport`)
source = source.replace(/function openReport\(id: number\) \{[\s\S]*?\n\}\n\nfunction openTask/, `function openReport(id: number) {
  if (!detail.value) {
    return
  }
  router.push({
    path: '/automation/api',
    query: { workspace: detail.value.workspaceCode, reportId: String(id) },
  })
}

function openTask`)
source = source.replace(/function openTask\(id: number\) \{[\s\S]*?\n\}\n\nfunction submitTransition/, `function openTask(id: number) {
  if (!detail.value) {
    return
  }
  router.push({
    path: '/automation/api',
    query: { workspace: detail.value.workspaceCode, taskId: String(id) },
  })
}

function submitTransition`)

fs.writeFileSync(path, source)
