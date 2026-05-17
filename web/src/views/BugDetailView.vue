<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import BugDetailContent from '../components/BugDetailContent.vue'
import { platformApi } from '../api/platform'
import { useWorkspace } from '../composables/useWorkspace'
import { useWorkspaceAccess } from '../composables/useWorkspaceAccess'
import type { BugDetail } from '../types/api'

const route = useRoute()
const router = useRouter()
const { workspaceCode } = useWorkspace()
const { canWriteWorkspace } = useWorkspaceAccess()

const loading = ref(false)
const transitioning = ref(false)
const commenting = ref(false)
const attachmentUploading = ref(false)
const attachmentRemovingId = ref<number | null>(null)
const detail = ref<BugDetail | null>(null)

const bugId = computed(() => {
  const raw = route.params.id?.toString()
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : null
})

const canWrite = computed(() => !!detail.value && canWriteWorkspace(detail.value.workspaceCode))

watch([bugId, workspaceCode], () => {
  void loadDetail()
}, { immediate: true })

watch(() => route.query.tab?.toString(), async () => {
  if (!detail.value) {
    return
  }
  await nextTick()
  scrollToTabSection()
})

async function loadDetail() {
  if (!bugId.value) {
    detail.value = null
    return
  }
  loading.value = true
  try {
    detail.value = await platformApi.getBugDetail(workspaceCode.value, bugId.value)
    await nextTick()
    scrollToTabSection()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    loading.value = false
  }
}

function scrollToTabSection() {
  const tab = route.query.tab?.toString()
  if (!tab) {
    return
  }
  const target = document.querySelector<HTMLElement>(`[data-bug-section="${tab}"]`)
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function goBack() {
  router.push({ path: '/bugs', query: { workspace: workspaceCode.value } })
}

function openEdit() {
  if (!detail.value) {
    return
  }
  router.push({
    path: `/bugs/${detail.value.id}/edit`,
    query: { workspace: detail.value.workspaceCode },
  })
}

function openCase(id: number) {
  if (!detail.value) {
    return
  }
  router.push({
    path: `/cases/manage/execute/${id}`,
    query: { workspace: detail.value.workspaceCode },
  })
}

function openReport(id: number) {
  if (!detail.value) {
    return
  }
  router.push({
    path: '/automation/api',
    query: { workspace: detail.value.workspaceCode, reportId: String(id) },
  })
}

function openTask(id: number) {
  if (!detail.value) {
    return
  }
  router.push({
    path: '/automation/api',
    query: { workspace: detail.value.workspaceCode, taskId: String(id) },
  })
}

async function submitTransition(payload: { status: string, comment: string }) {
  if (!detail.value) {
    return
  }
  transitioning.value = true
  try {
    await platformApi.transitionBug(detail.value.workspaceCode, detail.value.id, payload.status, payload.comment)
    await loadDetail()
    ElMessage.success('状态已更新')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    transitioning.value = false
  }
}

async function submitComment(content: string) {
  if (!detail.value) {
    return
  }
  commenting.value = true
  try {
    await platformApi.addBugComment(detail.value.workspaceCode, detail.value.id, content)
    await loadDetail()
    ElMessage.success('评论已添加')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    commenting.value = false
  }
}

async function uploadAttachments(files: File[]) {
  if (!detail.value) {
    return
  }
  attachmentUploading.value = true
  try {
    await platformApi.uploadBugAttachment(detail.value.workspaceCode, detail.value.id, files)
    await loadDetail()
    ElMessage.success(`已上传 ${files.length} 个附件`)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    attachmentUploading.value = false
  }
}

async function downloadAttachment(attachmentId: number) {
  if (!detail.value) {
    return
  }
  const attachment = detail.value.attachments.find(item => item.id === attachmentId)
  if (!attachment) {
    return
  }
  try {
    await platformApi.downloadBugAttachment(detail.value.workspaceCode, detail.value.id, attachmentId, attachment.fileName)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function removeAttachment(attachmentId: number) {
  if (!detail.value) {
    return
  }
  attachmentRemovingId.value = attachmentId
  try {
    await platformApi.deleteBugAttachment(detail.value.workspaceCode, detail.value.id, attachmentId)
    await loadDetail()
    ElMessage.success('附件已删除')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    attachmentRemovingId.value = null
  }
}
</script>

<template>
  <section v-loading="loading" class="bug-detail-page">
    <div v-if="detail" class="bug-detail-shell">
      <BugDetailContent
        :detail="detail"
        mode="page"
        :can-write="canWrite"
        :transitioning="transitioning"
        :commenting="commenting"
        :attachment-uploading="attachmentUploading"
        :attachment-removing-id="attachmentRemovingId"
        :show-back-action="true"
        @back="goBack"
        @edit="openEdit"
        @transition="submitTransition"
        @comment="submitComment"
        @upload-attachments="uploadAttachments"
        @download-attachment="downloadAttachment"
        @remove-attachment="removeAttachment"
        @open-case="openCase"
        @open-report="openReport"
        @open-task="openTask"
      />
    </div>
  </section>
</template>

<style scoped>
.bug-detail-page {
  display: flex;
  min-height: 0;
  height: 100%;
}

.bug-detail-shell {
  flex: 1;
  min-height: 0;
}
</style>
