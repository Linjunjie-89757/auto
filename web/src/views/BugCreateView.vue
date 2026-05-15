<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { platformApi, resolveApiUrl } from '../api/platform'
import BugEditorForm from '../components/BugEditorForm.vue'
import { useWorkspace } from '../composables/useWorkspace'
import type { BugDetail, CreateBugPayload, UpdateBugPayload, UserItem, WorkspaceItem } from '../types/api'

type PendingBugFile = {
  id: string
  file: File
  kind: 'attachment' | 'screenshot'
  previewUrl: string | null
}

type PendingInlineImage = {
  src: string
  file: File
}

const router = useRouter()
const route = useRoute()
const { workspaceCode, isAllScope } = useWorkspace()

const loading = ref(false)
const saving = ref(false)
const users = ref<UserItem[]>([])
const workspaces = ref<WorkspaceItem[]>([])
const pendingFiles = ref<PendingBugFile[]>([])
const pendingInlineImages = ref<PendingInlineImage[]>([])
const editingBug = ref<BugDetail | null>(null)

const form = reactive<CreateBugPayload & { workspaceCode: string }>({
  workspaceCode: workspaceCode.value === 'ALL' ? '' : workspaceCode.value,
  title: '',
  description: '',
  priority: 'P1',
  severity: 'HIGH',
  assigneeId: null,
  tags: [],
})

const pendingFileViews = computed(() => pendingFiles.value.map(item => ({
  id: item.id,
  name: item.file.name,
  size: item.file.size,
  kind: item.kind,
  previewUrl: item.previewUrl,
})))

const bugId = computed(() => {
  const rawId = route.params.id
  const id = Array.isArray(rawId) ? rawId[0] : rawId
  return id ? Number(id) : null
})
const isEditMode = computed(() => bugId.value !== null && Number.isFinite(bugId.value))
const pageTitle = computed(() => (isEditMode.value ? '更新缺陷' : '创建缺陷'))
const primarySubmitText = computed(() => (isEditMode.value ? '更新' : '创建'))

const canSubmit = computed(() => {
  if (isAllScope.value && !form.workspaceCode) {
    return false
  }
  return form.title.trim().length > 0 && hasRichDescriptionContent(form.description)
})

onMounted(loadOptions)

onBeforeUnmount(() => {
  clearPendingFiles()
  clearPendingInlineImages()
})

async function loadOptions() {
  loading.value = true
  try {
    const [userList, workspaceList] = await Promise.all([
      platformApi.getUsers(),
      platformApi.getSwitchableWorkspaces(),
    ])
    users.value = userList
    workspaces.value = workspaceList.filter(item => !item.allScope)
    if (isEditMode.value) {
      await loadBugDetail()
    }
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    loading.value = false
  }
}

async function loadBugDetail() {
  if (!bugId.value) {
    ElMessage.error('缺陷不存在')
    goBack()
    return
  }
  const detail = await platformApi.getBugDetail(workspaceCode.value, bugId.value)
  editingBug.value = detail
  form.workspaceCode = detail.workspaceCode
  form.title = detail.title
  form.description = detail.description
  form.priority = detail.priority
  form.severity = detail.severity
  form.assigneeId = detail.assigneeId
  form.tags = [...detail.tags]
}

function goBack() {
  router.push({ path: '/bugs', query: { workspace: workspaceCode.value } })
}

async function confirmLeavePage() {
  try {
    await ElMessageBox.confirm(
      '系统不会保存您所做的更改',
      '离开此页面？',
      {
        type: 'warning',
        confirmButtonText: '离开',
        cancelButtonText: '留下',
      },
    )
    goBack()
  }
  catch {
    // 用户选择留下，不做处理。
  }
}

function addPendingBugFiles(files: File[]) {
  const nextItems = files.map((file, index) => {
    const isImage = file.type.startsWith('image/')
    return {
      id: `${isImage ? 'screenshot' : 'attachment'}-${Date.now()}-${index}-${file.name}`,
      file,
      kind: isImage ? 'screenshot' as const : 'attachment' as const,
      previewUrl: isImage ? URL.createObjectURL(file) : null,
    }
  })
  pendingFiles.value = [...pendingFiles.value, ...nextItems]
}

function removePendingBugFile(id: string) {
  const target = pendingFiles.value.find(item => item.id === id)
  if (target?.previewUrl) {
    URL.revokeObjectURL(target.previewUrl)
  }
  pendingFiles.value = pendingFiles.value.filter(item => item.id !== id)
}

function clearPendingFiles() {
  pendingFiles.value.forEach((item) => {
    if (item.previewUrl) {
      URL.revokeObjectURL(item.previewUrl)
    }
  })
  pendingFiles.value = []
}

function addPendingInlineImage(payload: PendingInlineImage) {
  pendingInlineImages.value = [...pendingInlineImages.value, payload]
}

function clearPendingInlineImages() {
  pendingInlineImages.value.forEach((item) => {
    URL.revokeObjectURL(item.src)
  })
  pendingInlineImages.value = []
}

async function uploadPendingInlineImages(bugId: number, targetWorkspaceCode: string, html: string) {
  let nextHtml = html
  for (const item of pendingInlineImages.value) {
    if (!nextHtml.includes(item.src)) {
      URL.revokeObjectURL(item.src)
      continue
    }
    const [attachment] = await platformApi.uploadBugAttachment(targetWorkspaceCode, bugId, [item.file])
    const imageUrl = resolveApiUrl(attachment.downloadUrl || `/bugs/${bugId}/attachments/${attachment.id}/download`)
    nextHtml = nextHtml.split(item.src).join(imageUrl)
    URL.revokeObjectURL(item.src)
  }
  pendingInlineImages.value = []
  return nextHtml
}

function resetFormForNextCreate() {
  form.title = ''
  form.description = ''
  form.priority = 'P1'
  form.severity = 'HIGH'
  form.assigneeId = null
  form.tags = []
  if (isAllScope.value) {
    form.workspaceCode = ''
  }
  clearPendingFiles()
  clearPendingInlineImages()
}

async function submitBug(keepOpen = false) {
  if (!canSubmit.value) {
    ElMessage.warning('请先补充必填信息')
    return
  }
  saving.value = true
  try {
    const targetWorkspaceCode = isEditMode.value
      ? (editingBug.value?.workspaceCode ?? form.workspaceCode)
      : (isAllScope.value ? form.workspaceCode : workspaceCode.value)
    if (isEditMode.value) {
      if (!editingBug.value || !bugId.value) {
        ElMessage.error('缺陷不存在')
        return
      }
      const description = await uploadPendingInlineImages(bugId.value, targetWorkspaceCode, form.description.trim())
      form.description = description
      const payload: UpdateBugPayload = {
        workspaceCode: isAllScope.value ? editingBug.value.workspaceCode : undefined,
        title: form.title.trim(),
        description,
        priority: form.priority,
        severity: form.severity,
        assigneeId: form.assigneeId,
        relatedCaseId: editingBug.value.relatedCaseId,
        tags: form.tags,
      }
      const updatedBug = await platformApi.updateBug(workspaceCode.value, bugId.value, payload)
      if (pendingFiles.value.length) {
        await platformApi.uploadBugAttachment(targetWorkspaceCode, updatedBug.id, pendingFiles.value.map(item => item.file))
      }
      ElMessage.success('缺陷更新成功')
      goBack()
      return
    }

    const payload: CreateBugPayload = {
      workspaceCode: isAllScope.value ? form.workspaceCode : undefined,
      title: form.title.trim(),
      description: form.description.trim(),
      priority: form.priority,
      severity: form.severity,
      assigneeId: form.assigneeId,
      tags: form.tags,
    }
    const createdBug = await platformApi.createBug(workspaceCode.value, payload)
    const description = await uploadPendingInlineImages(createdBug.id, targetWorkspaceCode, payload.description)
    if (pendingFiles.value.length) {
      await platformApi.uploadBugAttachment(targetWorkspaceCode, createdBug.id, pendingFiles.value.map(item => item.file))
    }
    if (description !== payload.description) {
      await platformApi.updateBug(workspaceCode.value, createdBug.id, {
        workspaceCode: isAllScope.value ? form.workspaceCode : undefined,
        title: payload.title,
        description,
        priority: payload.priority,
        severity: payload.severity,
        assigneeId: payload.assigneeId,
        tags: payload.tags,
      })
    }
    ElMessage.success('缺陷创建成功')
    if (keepOpen) {
      resetFormForNextCreate()
      return
    }
    goBack()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

function extractPlainTextFromHtml(html: string) {
  return html
    .replace(/<style[\s\S]*?<\/style>/gi, '')
    .replace(/<script[\s\S]*?<\/script>/gi, '')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/p>/gi, '\n')
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .trim()
}

function hasRichDescriptionContent(html: string) {
  return extractPlainTextFromHtml(html).length > 0 || /<img\b[^>]*\bsrc=/i.test(html)
}
</script>

<template>
  <section v-loading="loading" class="bug-create-page">
    <div class="bug-create-shell">
      <header class="bug-create-header">
        <div class="bug-create-backbar">
          <el-button class="bug-create-back-button" text :icon="ArrowLeft" @click="confirmLeavePage">
            返回缺陷管理
          </el-button>
        </div>
        <div class="bug-create-titlebar">
          <h1 class="bug-create-title">{{ pageTitle }}</h1>
        </div>
      </header>

      <div class="bug-create-content">
        <BugEditorForm
          page-mode
          :form="form"
          :saving="saving"
          :users="users"
          :show-workspace="isAllScope && !isEditMode"
          :workspaces="workspaces"
          :pending-files="pendingFileViews"
          @add-files="addPendingBugFiles"
          @remove-file="removePendingBugFile"
          @add-inline-image="addPendingInlineImage"
        />
      </div>

      <footer class="bug-create-footer">
        <el-button @click="confirmLeavePage">取消</el-button>
        <el-button v-if="!isEditMode" :loading="saving" :disabled="!canSubmit" @click="submitBug(true)">
          保存并继续创建
        </el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSubmit" @click="submitBug(false)">
          {{ primarySubmitText }}
        </el-button>
      </footer>
    </div>
  </section>
</template>

<style scoped>
.bug-create-page {
  display: flex;
  height: 100%;
  min-height: 0;
  padding: 0;
}

.bug-create-shell {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) 58px;
  flex: 1;
  min-height: 0;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fff;
  box-shadow: var(--shadow-soft);
  overflow: hidden;
}

.bug-create-header {
  display: grid;
  border-bottom: 1px solid #ebeef5;
}

.bug-create-backbar {
  display: flex;
  align-items: center;
  padding: 12px 24px 0;
}

.bug-create-back-button {
  padding: 0;
  font-size: 13px;
  font-weight: 600;
  color: #175cd3;
}

.bug-create-back-button:hover,
.bug-create-back-button:focus-visible {
  color: #1849a9;
}

.bug-create-titlebar {
  display: flex;
  align-items: center;
  padding: 18px 24px 18px;
}

.bug-create-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  line-height: 24px;
  font-family: "Helvetica Neue", Arial, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
  color: #323233;
}

.bug-create-content {
  min-height: 0;
  overflow: auto;
}

.bug-create-footer {
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  min-height: 58px;
  padding: 12px 22px;
  border-top: 1px solid #ebeef5;
  background: #fff;
}

.bug-create-footer :deep(.el-button) {
  min-width: 64px;
  height: 32px;
  margin-left: 0;
  border-radius: 4px;
  font-size: 14px;
}

.bug-create-footer :deep(.el-button + .el-button) {
  margin-left: 0;
}

.bug-create-footer :deep(.el-button--primary) {
  min-width: 62px;
}
</style>
