<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Close, Edit, EditPen, Link, MoreFilled, Paperclip } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import type { BugActivity, BugAttachment, BugDetail, BugSummary, UpdateBugPayload, UserItem } from '../types/api'
import BugCaseAssociateDialog from './BugCaseAssociateDialog.vue'
import BugRichTextEditor from './BugRichTextEditor.vue'
import {
  formatBugDateTime,
  formatBugSeverity,
  formatBugStatus,
  isImageFile,
} from '../utils/bugPresentation'
import { resolveApiUrl } from '../api/platform'

const props = withDefaults(defineProps<{
  modelValue: boolean
  detail: BugDetail | null
  summary?: Pick<BugSummary, 'bugNo' | 'title' | 'workspaceName' | 'status' | 'assigneeName'> | null
  users?: UserItem[]
  loading?: boolean
  transitioning?: boolean
  commenting?: boolean
  assigning?: boolean
  basicSaving?: boolean
  descriptionSaving?: boolean
  associatingCase?: boolean
  attachmentUploading?: boolean
  attachmentRemovingId?: number | null
  canWrite?: boolean
}>(), {
  summary: null,
  users: () => [],
  loading: false,
  transitioning: false,
  commenting: false,
  assigning: false,
  basicSaving: false,
  descriptionSaving: false,
  associatingCase: false,
  attachmentUploading: false,
  attachmentRemovingId: null,
  canWrite: false,
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'transition', payload: { status: string, comment: string }): void
  (event: 'comment', content: string): void
  (event: 'edit'): void
  (event: 'save-description', content: string): void
  (event: 'add-inline-image', payload: { file: File, src: string }): void
  (event: 'save-basic', payload: UpdateBugPayload): void
  (event: 'associate-case', caseId: number): void
  (event: 'unlink-case'): void
  (event: 'upload-attachments', files: File[]): void
  (event: 'download-attachment', attachmentId: number): void
  (event: 'remove-attachment', attachmentId: number): void
}>()

type DrawerTab = 'basic' | 'detail' | 'case' | 'comment' | 'history'

type BasicFormState = {
  assigneeId: number | null
  status: string
  severity: string
  tags: string[]
}

const router = useRouter()
const uploadInput = ref<HTMLInputElement | null>(null)
const activeTab = ref<DrawerTab>('basic')
const commentText = ref('')
const descriptionEditing = ref(false)
const descriptionDraft = ref('')
const richImagePreviewVisible = ref(false)
const richImagePreviewUrls = ref<string[]>([])
const activeRichImagePreviewIndex = ref(0)
const caseKeyword = ref('')
const associateDialogVisible = ref(false)
const basicForm = ref<BasicFormState>({
  assigneeId: null,
  status: 'TODO',
  severity: 'HIGH',
  tags: [],
})

const severityOptions = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
const statusOptions = [
  { label: '未指派', value: 'TODO' },
  { label: '已指派', value: 'ASSIGNED' },
  { label: '处理中', value: 'IN_PROGRESS' },
  { label: '待验证', value: 'PENDING_VERIFY' },
  { label: '已关闭', value: 'CLOSED' },
  { label: '已拒绝', value: 'REJECTED' },
]
const basicFieldText = {
  assignee: '\u5904\u7406\u4eba',
  status: '\u72b6\u6001',
  severity: '\u4e25\u91cd\u7a0b\u5ea6',
  tags: '\u6807\u7b7e',
  selectPlaceholder: '\u8bf7\u9009\u62e9',
  tagPlaceholder: '\u8f93\u5165\u5185\u5bb9\u540e\u56de\u8f66\u53ef\u76f4\u63a5\u6dfb\u52a0\u6807\u7b7e',
}
const descriptionEditorBaseLineCount = 4
const descriptionEditorLineHeight = 24
const descriptionEditorVerticalPadding = 32

const tabItems: Array<{ key: DrawerTab, label: string }> = [
  { key: 'basic', label: '基本信息' },
  { key: 'detail', label: '详情' },
  { key: 'case', label: '用例' },
  { key: 'comment', label: '评论' },
  { key: 'history', label: '变更历史' },
]

const imageAttachments = computed(() => (
  props.detail?.attachments.filter(item => isImageFile(item.contentType, item.fileName)) ?? []
))

const fileAttachments = computed(() => (
  props.detail?.attachments.filter(item => !isImageFile(item.contentType, item.fileName)) ?? []
))

const authorizedImageUrls = ref<Record<string, string>>({})
const authorizedImageLoadVersion = ref(0)
const previewUrls = computed(() => imageAttachments.value
  .map((item) => {
    const rawUrl = item.downloadUrl || ''
    return getAuthorizedImageUrl(rawUrl)
  }))
const descriptionHtml = computed(() => sanitizeRichHtml(
  props.detail?.description || '',
  imageAttachments.value,
  authorizedImageUrls.value,
))

const syncingBasicForm = ref(false)
const basicAutoSaveTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const statusAutoSaveTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const lastBasicSubmitSignature = ref('')
const lastStatusSubmitValue = ref('')

const basicFormSignature = computed(() => JSON.stringify({
  assigneeId: basicForm.value.assigneeId,
  severity: basicForm.value.severity,
  tags: [...basicForm.value.tags].sort(),
}))

const basicDirty = computed(() => {
  if (!props.detail) {
    return false
  }
  const currentTags = [...(props.detail.tags ?? [])].sort()
  const draftTags = [...basicForm.value.tags].sort()
  return basicForm.value.assigneeId !== props.detail.assigneeId
    || basicForm.value.severity !== props.detail.severity
    || draftTags.join('|') !== currentTags.join('|')
})

const statusDirty = computed(() => {
  if (!props.detail) {
    return false
  }
  return basicForm.value.status !== props.detail.status
})

const descriptionEditorMinHeight = computed(() => {
  const visibleLineCount = countVisibleEditorLines(descriptionDraft.value)
  const displayLineCount = visibleLineCount < descriptionEditorBaseLineCount
    ? descriptionEditorBaseLineCount
    : visibleLineCount + 1
  return displayLineCount * descriptionEditorLineHeight + descriptionEditorVerticalPadding
})

const caseRows = computed(() => {
  const summary = props.detail?.sourceContext.caseSummary
  if (!summary) {
    return []
  }
  const keyword = caseKeyword.value.trim().toLowerCase()
  const matches = !keyword
    || summary.caseNo.toLowerCase().includes(keyword)
    || summary.title.toLowerCase().includes(keyword)
  if (!matches) {
    return []
  }
  return [{
    id: summary.id,
    caseNo: summary.caseNo,
    title: summary.title,
    workspaceName: summary.workspaceName,
    caseType: '??????',
  }]
})

watch(() => props.modelValue, (value) => {
  if (value) {
    activeTab.value = 'basic'
  }
  else {
    closeRichImagePreview()
  }
})

watch(
  [() => basicForm.value.assigneeId, () => basicForm.value.severity, () => basicForm.value.tags.slice().join('|')],
  () => {
    scheduleBasicAutoSave()
  }
)

watch(
  () => basicForm.value.status,
  () => {
    scheduleStatusAutoSave()
  }
)

watch(
  () => [props.basicSaving, props.assigning, props.transitioning],
  ([basicSaving, assigning, transitioning]) => {
    if (!basicSaving && !assigning && basicDirty.value && basicFormSignature.value !== lastBasicSubmitSignature.value) {
      scheduleBasicAutoSave()
    }
    if (!transitioning && statusDirty.value && basicForm.value.status !== lastStatusSubmitValue.value) {
      scheduleStatusAutoSave()
    }
  }
)

watch(
  () => props.detail,
  (detail) => {
    commentText.value = ''
    caseKeyword.value = ''
    descriptionEditing.value = false
    descriptionDraft.value = normalizeInlineImageSources(
      detail?.description || '',
      detail?.attachments ?? [],
    )
    syncingBasicForm.value = true
    basicForm.value = {
      assigneeId: detail?.assigneeId ?? null,
      status: detail?.status ?? 'TODO',
      severity: detail?.severity ?? 'HIGH',
      tags: [...(detail?.tags ?? [])],
    }
    lastBasicSubmitSignature.value = basicFormSignature.value
    lastStatusSubmitValue.value = detail?.status ?? 'TODO'
    setTimeout(() => {
      syncingBasicForm.value = false
    }, 0)
  },
  { immediate: true },
)

watch(
  () => props.detail,
  (detail) => {
    revokeAuthorizedImageUrls()
    if (!detail) {
      return
    }
    void loadAuthorizedImageUrls(detail)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  revokeAuthorizedImageUrls()
  closeRichImagePreview()
})

function closeDrawer() {
  emit('update:modelValue', false)
}

function requestUpload() {
  if (!props.canWrite) {
    return
  }
  uploadInput.value?.click()
}

function handleUploadChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  if (!files.length) {
    return
  }
  emit('upload-attachments', files)
}

async function copyShareLink() {
  if (!props.detail) {
    return
  }
  const url = new URL(router.resolve({
    path: `/bugs/${props.detail.id}`,
    query: { workspace: props.detail.workspaceCode },
  }).href, window.location.origin).toString()

  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制')
  }
  catch {
    ElMessage.error('链接复制失败')
  }
}

function copyBugStylePlaceholder() {
  if (!props.detail) {
    return
  }
  navigator.clipboard.writeText(props.detail.bugNo).then(() => {
    ElMessage.success('缺陷编号已复制')
  }).catch(() => {
    ElMessage.error('缺陷编号复制失败')
  })
}

function deleteBugStylePlaceholder() {
  ElMessage.info('删除能力暂未接入后端，先保留占位入口')
}

function openCase(id: number) {
  if (!props.detail) {
    return
  }
  router.push({
    path: `/cases/manage/execute/${id}`,
    query: { workspace: props.detail.workspaceCode },
  })
}

function submitComment() {
  const content = commentText.value.trim()
  if (!content) {
    return
  }
  emit('comment', content)
  commentText.value = ''
}

function startDescriptionEdit() {
  descriptionDraft.value = normalizeInlineImageSources(
    props.detail?.description || '',
    imageAttachments.value,
  )
  descriptionEditing.value = true
}

function cancelDescriptionEdit() {
  descriptionDraft.value = normalizeInlineImageSources(
    props.detail?.description || '',
    imageAttachments.value,
  )
  descriptionEditing.value = false
}

function submitDescriptionEdit() {
  emit('save-description', descriptionDraft.value)
}

function handleRichContentClick(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof HTMLImageElement)) {
    return
  }
  const container = event.currentTarget
  if (!(container instanceof HTMLElement)) {
    return
  }
  const images = Array.from(container.querySelectorAll('img'))
    .map(image => image.getAttribute('data-preview-src') || image.getAttribute('src') || image.currentSrc || '')
    .filter(Boolean)
  if (!images.length) {
    return
  }
  const clickedSource = target.getAttribute('data-preview-src') || target.getAttribute('src') || target.currentSrc || ''
  const clickedIndex = Math.max(0, images.findIndex(source => source === clickedSource))
  openRichImagePreview(images, clickedIndex)
}

function openRichImagePreview(urls: string[], initialIndex = 0) {
  richImagePreviewUrls.value = urls
  activeRichImagePreviewIndex.value = Math.min(Math.max(initialIndex, 0), Math.max(urls.length - 1, 0))
  richImagePreviewVisible.value = true
}

function closeRichImagePreview() {
  richImagePreviewVisible.value = false
  richImagePreviewUrls.value = []
  activeRichImagePreviewIndex.value = 0
}

function handleRichImagePreviewSwitch(index: number) {
  activeRichImagePreviewIndex.value = index
}

async function loadAuthorizedImageUrls(detail: BugDetail) {
  const loadVersion = ++authorizedImageLoadVersion.value
  const nextUrls: Record<string, string> = {}
  for (const attachment of detail.attachments) {
    if (!isImageFile(attachment.contentType, attachment.fileName) || !attachment.downloadUrl) {
      continue
    }
    const rawUrl = attachment.downloadUrl
    const resolvedUrl = resolveApiUrl(rawUrl)
    try {
      const response = await fetch(resolvedUrl, {
        method: 'GET',
        credentials: 'include',
        headers: {
          'X-Workspace-Code': detail.workspaceCode,
        },
      })
      if (!response.ok) {
        continue
      }
      const blob = await response.blob()
      const objectUrl = URL.createObjectURL(blob)
      nextUrls[rawUrl] = objectUrl
      nextUrls[resolvedUrl] = objectUrl
      collectImageUrlKeys(rawUrl, resolvedUrl).forEach((key) => {
        nextUrls[key] = objectUrl
      })
    }
    catch {
      // ignore image preview fetch failures and fall back to direct URLs
    }
  }
  if (loadVersion !== authorizedImageLoadVersion.value) {
    Object.values(nextUrls).forEach((url) => {
      URL.revokeObjectURL(url)
    })
    return
  }
  revokeAuthorizedImageUrls()
  authorizedImageUrls.value = nextUrls
}

function revokeAuthorizedImageUrls() {
  authorizedImageLoadVersion.value += 1
  const uniqueUrls = new Set(Object.values(authorizedImageUrls.value))
  uniqueUrls.forEach((url) => {
    URL.revokeObjectURL(url)
  })
  authorizedImageUrls.value = {}
}

function getAuthorizedImageUrl(downloadUrl: string | null) {
  const rawUrl = downloadUrl || ''
  if (/^blob:|^data:/i.test(rawUrl)) {
    return rawUrl
  }
  const resolvedUrl = resolveApiUrl(rawUrl)
  const matchedUrl = collectImageUrlKeys(rawUrl, resolvedUrl)
    .map(key => authorizedImageUrls.value[key])
    .find(Boolean)
  return matchedUrl || resolvedUrl
}

function getMatchedAttachmentImageUrl(source: string, attachments: BugAttachment[]) {
  if (!source) {
    return ''
  }
  return attachments
    .filter(item => isImageFile(item.contentType, item.fileName) && !!item.downloadUrl)
    .map((item) => {
      const downloadUrl = item.downloadUrl || ''
      const authorizedUrl = getAuthorizedImageUrl(downloadUrl)
      const resolvedUrl = resolveApiUrl(downloadUrl)
      return {
        authorizedUrl,
        keys: collectImageUrlKeys(
          downloadUrl,
          resolvedUrl,
          authorizedUrl,
          item.fileName,
        ),
      }
    })
    .find(item => item.keys.includes(source))?.authorizedUrl || ''
}

function collectImageUrlKeys(...urls: string[]) {
  const keys = new Set<string>()
  urls.filter(Boolean).forEach((value) => {
    keys.add(value)
    try {
      const normalized = new URL(value, window.location.origin)
      keys.add(normalized.toString())
      keys.add(normalized.pathname)
      if (normalized.pathname && normalized.search) {
        keys.add(`${normalized.pathname}${normalized.search}`)
      }
    }
    catch {
      // ignore malformed URLs and keep original value as-is
    }
  })
  return Array.from(keys)
}

function normalizeInlineImageSources(
  content: string,
  attachments: BugAttachment[],
  imageUrlMap?: Record<string, string>,
) {
  if (!content.trim()) {
    return ''
  }
  const parser = new DOMParser()
  const doc = parser.parseFromString(`<div>${content}</div>`, 'text/html')
  const images = Array.from(doc.body.querySelectorAll('img')) as HTMLImageElement[]
  const attachmentUrls = attachments
    .filter(item => isImageFile(item.contentType, item.fileName) && !!item.downloadUrl)
    .map((item) => {
      const downloadUrl = item.downloadUrl || ''
      const resolved = imageUrlMap ? getAuthorizedImageUrl(downloadUrl) : resolveApiUrl(downloadUrl)
      return resolved || downloadUrl
    })
  let attachmentIndex = 0
  images.forEach((image) => {
    const source = image.getAttribute('src') || ''
    const matchedAttachmentSource = getMatchedAttachmentImageUrl(source, attachments)
    if (matchedAttachmentSource) {
      image.setAttribute('src', matchedAttachmentSource)
      image.setAttribute('data-preview-src', matchedAttachmentSource)
      return
    }

    const fallbackAttachmentSource = attachmentUrls[attachmentIndex] || ''
    const looksInlineTemporary = /^blob:|^data:/i.test(source)
    const looksExternal = /^(https?:\/\/|\/api\/)/i.test(source)
    if (looksInlineTemporary) {
      attachmentIndex += 1
      if (fallbackAttachmentSource) {
        image.setAttribute('src', fallbackAttachmentSource)
        image.setAttribute('data-preview-src', fallbackAttachmentSource)
      }
      return
    }

    if (!source || !looksExternal) {
      if (fallbackAttachmentSource) {
        attachmentIndex += 1
        image.setAttribute('src', fallbackAttachmentSource)
        image.setAttribute('data-preview-src', fallbackAttachmentSource)
        return
      }
    }

    const normalizedSource = source ? getAuthorizedImageUrl(source) : ''
    if (normalizedSource) {
      image.setAttribute('src', normalizedSource)
      image.setAttribute('data-preview-src', normalizedSource)
    }
  })
  return doc.body.innerHTML
}

function formatAttachmentFileSize(size: number | null) {
  if (size === null || Number.isNaN(size)) {
    return 'Unknown size'
  }
  if (size < 1024) {
    return `${size} B`
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(size >= 10 * 1024 ? 0 : 1)} KB`
  }
  return `${(size / (1024 * 1024)).toFixed(size >= 10 * 1024 * 1024 ? 0 : 1)} MB`
}

function getAttachmentTypeLabel(fileName: string, contentType: string | null) {
  const extension = fileName.split('.').pop()?.trim().toUpperCase()
  if (extension && extension.length <= 5) {
    return extension
  }
  if (contentType?.startsWith('text/')) {
    return 'TXT'
  }
  if (contentType?.includes('pdf')) {
    return 'PDF'
  }
  if (contentType?.includes('word')) {
    return 'DOC'
  }
  if (contentType?.includes('sheet') || contentType?.includes('excel')) {
    return 'XLS'
  }
  if (contentType?.includes('presentation') || contentType?.includes('powerpoint')) {
    return 'PPT'
  }
  if (contentType?.includes('zip') || contentType?.includes('rar') || contentType?.includes('7z')) {
    return 'ZIP'
  }
  return 'FILE'
}

function getAttachmentTypeTone(fileName: string, contentType: string | null) {
  const label = getAttachmentTypeLabel(fileName, contentType)
  if (label === 'PDF') {
    return 'pdf'
  }
  if (['DOC', 'DOCX'].includes(label)) {
    return 'word'
  }
  if (['XLS', 'XLSX', 'CSV'].includes(label)) {
    return 'excel'
  }
  if (['PPT', 'PPTX'].includes(label)) {
    return 'ppt'
  }
  if (['TXT', 'LOG', 'MD', 'RTF'].includes(label)) {
    return 'text'
  }
  if (['JSON', 'XML', 'YAML', 'YML'].includes(label)) {
    return 'code'
  }
  if (['ZIP', 'RAR', '7Z', 'TAR', 'GZ'].includes(label)) {
    return 'archive'
  }
  return 'file'
}

function formatAttachmentMeta(size: number | null, uploadedByName: string | null, createdAt: string | null) {
  const parts = [formatAttachmentFileSize(size)]
  if (uploadedByName) {
    parts.push(`${uploadedByName} 上传于`)
  }
  if (createdAt) {
    parts.push(formatBugDateTime(createdAt))
  }
  return parts.join(' · ')
}

function countVisibleEditorLines(content: string) {
  const normalized = content.replace(/\r\n/g, '\n').trim()
  if (!normalized) {
    return 1
  }
  if (!/<[a-z][\s\S]*>/i.test(normalized)) {
    return normalized.split('\n').length
  }

  const parser = typeof DOMParser === 'undefined' ? null : new DOMParser()
  if (!parser) {
    return normalized.split('\n').length
  }

  const doc = parser.parseFromString(normalized, 'text/html')
  const lines: string[] = []
  let currentLine = ''
  const lineBreakTags = new Set(['p', 'div', 'li', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6'])

  function commitLine(force = false) {
    if (force || currentLine.length > 0) {
      lines.push(currentLine)
      currentLine = ''
    }
  }

  function appendText(text: string) {
    if (!text) {
      return
    }
    const normalizedText = text.replace(/\u00a0/g, ' ')
    const segments = normalizedText.split('\n')
    segments.forEach((segment, index) => {
      currentLine += segment
      if (index < segments.length - 1) {
        commitLine(true)
      }
    })
  }

  function walk(node: Node) {
    if (node.nodeType === Node.TEXT_NODE) {
      appendText(node.textContent || '')
      return
    }
    if (!(node instanceof HTMLElement)) {
      return
    }

    const tagName = node.tagName.toLowerCase()
    if (tagName === 'br') {
      commitLine(true)
      return
    }

    const createsOwnLine = lineBreakTags.has(tagName)
    if (createsOwnLine && currentLine.length > 0) {
      commitLine(true)
    }

    if (tagName === 'img') {
      currentLine += '[image]'
    }
    else {
      Array.from(node.childNodes).forEach(walk)
    }

    if (createsOwnLine) {
      commitLine(true)
    }
  }

  Array.from(doc.body.childNodes).forEach(walk)
  commitLine(lines.length === 0)

  return Math.max(lines.length, 1)
}

function submitBasicSave() {
  if (!props.detail || !basicDirty.value) {
    return
  }
  lastBasicSubmitSignature.value = basicFormSignature.value
  emit('save-basic', {
    workspaceCode: props.detail.workspaceCode,
    title: props.detail.title,
    description: props.detail.description,
    priority: props.detail.priority,
    severity: basicForm.value.severity,
    assigneeId: basicForm.value.assigneeId,
    relatedCaseId: props.detail.relatedCaseId,
    tags: [...basicForm.value.tags],
  })
}

function scheduleBasicAutoSave() {
  if (!props.canWrite || syncingBasicForm.value || !basicDirty.value) {
    return
  }
  if (basicAutoSaveTimer.value) {
    clearTimeout(basicAutoSaveTimer.value)
  }
  basicAutoSaveTimer.value = setTimeout(() => {
    basicAutoSaveTimer.value = null
    if (props.basicSaving || props.assigning) {
      scheduleBasicAutoSave()
      return
    }
    if (basicFormSignature.value === lastBasicSubmitSignature.value) {
      return
    }
    submitBasicSave()
  }, 250)
}

function submitStatusAutoSave() {
  if (!props.detail || !statusDirty.value) {
    return
  }
  lastStatusSubmitValue.value = basicForm.value.status
  emit('transition', {
    status: basicForm.value.status,
    comment: '',
  })
}

function scheduleStatusAutoSave() {
  if (!props.canWrite || syncingBasicForm.value || !statusDirty.value) {
    return
  }
  if (statusAutoSaveTimer.value) {
    clearTimeout(statusAutoSaveTimer.value)
  }
  statusAutoSaveTimer.value = setTimeout(() => {
    statusAutoSaveTimer.value = null
    if (props.transitioning) {
      scheduleStatusAutoSave()
      return
    }
    if (basicForm.value.status === lastStatusSubmitValue.value) {
      return
    }
    submitStatusAutoSave()
  }, 250)
}

function openAssociateCaseDialog() {
  if (!props.detail || !props.canWrite) {
    return
  }
  associateDialogVisible.value = true
}

function handleCaseAssociated(caseId: number) {
  associateDialogVisible.value = false
  emit('associate-case', caseId)
}

function formatActivityDetail(item: BugActivity) {
  if (item.type === 'STATUS_CHANGED' || item.type === 'ASSIGNED') {
    const fromText = formatBugStatus(item.fromStatus)
    const toText = formatBugStatus(item.toStatus)
    return [item.fromStatus || item.toStatus ? `${fromText} -> ${toText}` : '', item.content].filter(Boolean).join(' | ')
  }
  if (item.type === 'ATTACHMENT_ADDED' || item.type === 'ATTACHMENT_REMOVED') {
    return item.attachmentName || item.content || '-'
  }
  return item.content || '-'
}

function sanitizeRichHtml(
  content: string,
  attachments: BugAttachment[],
  imageUrlMap: Record<string, string>,
) {
  if (!content.trim()) {
    return '<p class="bug-drawer-empty-text">暂无缺陷内容</p>'
  }
  const parser = new DOMParser()
  const normalizedContent = normalizeInlineImageSources(content, attachments, imageUrlMap)
  const doc = parser.parseFromString(`<div>${normalizedContent}</div>`, 'text/html')
  const allowedTags = new Set([
    'DIV', 'BR', 'P', 'SPAN', 'STRONG', 'B', 'EM', 'I', 'U', 'S', 'MARK',
    'UL', 'OL', 'LI', 'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'LABEL', 'INPUT', 'IMG',
  ])
  const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_ELEMENT)
  const toUnwrap: Element[] = []

  while (walker.nextNode()) {
    const element = walker.currentNode as HTMLElement
    if (!allowedTags.has(element.tagName)) {
      toUnwrap.push(element)
      continue
    }
    Array.from(element.attributes).forEach((attr) => {
      if (attr.name.startsWith('on')) {
        element.removeAttribute(attr.name)
        return
      }
      if (attr.name === 'style') {
        const safeStyle = sanitizeStyle(attr.value)
        if (safeStyle) {
          element.setAttribute('style', safeStyle)
        }
        else {
          element.removeAttribute(attr.name)
        }
        return
      }
      if (attr.name === 'data-type' && /^(taskList|taskItem)$/i.test(attr.value)) {
        return
      }
      if (attr.name === 'data-checked' && /^(true|false)$/i.test(attr.value)) {
        return
      }
      if (element.tagName === 'INPUT' && attr.name === 'type' && attr.value === 'checkbox') {
        return
      }
      if (element.tagName === 'INPUT' && ['checked', 'disabled'].includes(attr.name)) {
        return
      }
      if (element.tagName === 'IMG' && ['src', 'alt', 'title', 'data-preview-src'].includes(attr.name)) {
        if (
          !['src', 'data-preview-src'].includes(attr.name)
          || /^(https?:\/\/|\/api\/|blob:|data:image\/)/i.test(attr.value)
        ) {
          return
        }
      }
      element.removeAttribute(attr.name)
    })
    if (element.tagName === 'IMG') {
      const source = element.getAttribute('src')
      const previewSource = element.getAttribute('data-preview-src')
      if (source && !/^blob:|^data:/i.test(source)) {
        const resolvedSource = resolveApiUrl(source)
        const authorizedSource = collectImageUrlKeys(source, resolvedSource)
          .map(key => imageUrlMap[key])
          .find(Boolean)
        if (authorizedSource) {
          element.setAttribute('src', authorizedSource)
          element.setAttribute('data-preview-src', authorizedSource)
        }
      }
      else if (previewSource) {
        element.setAttribute('data-preview-src', previewSource)
      }
    }
    if (element.tagName === 'INPUT') {
      element.setAttribute('disabled', 'disabled')
    }
  }

  toUnwrap.forEach((element) => {
    const parent = element.parentNode
    if (!parent) {
      return
    }
    while (element.firstChild) {
      parent.insertBefore(element.firstChild, element)
    }
    parent.removeChild(element)
  })

  return doc.body.innerHTML || '<p class="bug-drawer-empty-text">暂无缺陷内容</p>'
}

function sanitizeStyle(value: string) {
  const rules = value.split(';').map(item => item.trim()).filter(Boolean)
  const safeRules: string[] = []
  rules.forEach((rule) => {
    const [rawName, rawValue] = rule.split(':')
    const name = rawName?.trim().toLowerCase()
    const styleValue = rawValue?.trim()
    if (!name || !styleValue) {
      return
    }
    if (name === 'font-size' && /^(12|14|16|18)px$/i.test(styleValue)) {
      safeRules.push(`font-size: ${styleValue}`)
      return
    }
    if (name === 'font-family' && /^(Arial|"PingFang SC"|"Microsoft YaHei"|SimSun|monospace)$/i.test(styleValue)) {
      safeRules.push(`font-family: ${styleValue}`)
      return
    }
    if (name === 'text-align' && /^(left|center|right|justify)$/i.test(styleValue)) {
      safeRules.push(`text-align: ${styleValue}`)
    }
  })
  return safeRules.join('; ')
}
</script>

<template>
  <el-drawer
      :model-value="modelValue"
      :with-header="false"
      append-to-body
      close-on-click-modal
      :show-close="false"
      size="850px"
      class="ms-bug-detail-drawer"
      @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="ms-bug-detail-shell">
      <input ref="uploadInput" type="file" multiple class="bug-hidden-input" @change="handleUploadChange">

      <div class="ms-bug-detail-topbar">
        <div class="ms-bug-detail-title-wrap">
          <div class="ms-bug-detail-object-line">
            <span class="ms-bug-detail-object-no">{{ detail?.bugNo || summary?.bugNo || '-' }}</span>
            <span class="ms-bug-detail-object-name">{{ detail?.title || summary?.title || '未命名缺陷' }}</span>
            <el-tag v-if="detail" effect="plain" size="small" class="ms-bug-detail-status-tag">
              {{ formatBugStatus(detail.status) }}
            </el-tag>
          </div>
        </div>

        <div class="ms-bug-detail-top-actions">
          <el-button text :icon="Edit" @click="emit('edit')">编辑</el-button>
          <el-button text :icon="Link" @click="copyShareLink">分享</el-button>
          <el-dropdown trigger="click" placement="bottom-end">
            <el-button text :icon="MoreFilled">更多</el-button>
            <template #dropdown>
              <el-dropdown-menu class="ms-bug-detail-more-menu">
                <el-dropdown-item @click="copyBugStylePlaceholder">复制</el-dropdown-item>
                <el-dropdown-item class="is-danger" @click="deleteBugStylePlaceholder">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button text class="ms-bug-detail-close" :icon="Close" @click="closeDrawer" />
        </div>
      </div>

      <div class="ms-bug-detail-tabs">
        <button
          v-for="tab in tabItems"
          :key="tab.key"
          type="button"
          :class="['ms-bug-detail-tab', { 'is-active': activeTab === tab.key }]"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-loading="loading" class="ms-bug-detail-content">
        <template v-if="detail">
          <section v-show="activeTab === 'basic'" class="ms-bug-detail-pane">
            <div class="ms-bug-basic-shell">
              <div class="ms-bug-basic-panel ms-bug-basic-panel-meter">
                <div class="ms-bug-basic-row ms-bug-basic-row-meter">
                  <span class="ms-bug-basic-label">{{ basicFieldText.assignee }}<em>*</em></span>
                  <div class="ms-bug-basic-control">
                    <el-select
                      v-model="basicForm.assigneeId"
                      clearable
                      :disabled="!canWrite || !users.length"
                      class="ms-bug-basic-select"
                      :placeholder="basicFieldText.selectPlaceholder"
                    >
                      <el-option v-for="item in users" :key="item.id" :label="item.displayName" :value="item.id" />
                    </el-select>
                  </div>
                </div>

                <div class="ms-bug-basic-row ms-bug-basic-row-meter">
                  <span class="ms-bug-basic-label">{{ basicFieldText.status }}<em>*</em></span>
                  <div class="ms-bug-basic-control">
                    <el-select
                      v-model="basicForm.status"
                      class="ms-bug-basic-select"
                      :placeholder="basicFieldText.selectPlaceholder"
                      :disabled="!canWrite"
                    >
                      <el-option
                        v-for="item in statusOptions"
                        :key="item.value"
                        :label="item.label"
                        :value="item.value"
                      />
                    </el-select>
                  </div>
                </div>

                <div class="ms-bug-basic-row ms-bug-basic-row-meter">
                  <span class="ms-bug-basic-label">{{ basicFieldText.severity }} <em>*</em></span>
                  <div class="ms-bug-basic-control">
                    <el-select
                      v-model="basicForm.severity"
                      :disabled="!canWrite"
                      class="ms-bug-basic-select"
                      :placeholder="basicFieldText.selectPlaceholder"
                    >
                      <el-option v-for="item in severityOptions" :key="item" :label="formatBugSeverity(item)" :value="item" />
                    </el-select>
                  </div>
                </div>

                <div class="ms-bug-basic-row ms-bug-basic-row-meter ms-bug-basic-row-wide">
                  <span class="ms-bug-basic-label">{{ basicFieldText.tags }}</span>
                  <div class="ms-bug-basic-control">
                    <el-select
                      v-model="basicForm.tags"
                      class="ms-bug-basic-select ms-bug-tag-select"
                      multiple
                      filterable
                      allow-create
                      default-first-option
                      :reserve-keyword="false"
                      :teleported="false"
                      :disabled="!canWrite"
                      popper-class="bug-editor-tag-popper"
                      :placeholder="basicFieldText.tagPlaceholder"
                    />
                  </div>
                </div>
              </div>
            </div>
          </section>

          <section v-show="activeTab === 'detail'" class="ms-bug-detail-pane">
            <div class="ms-bug-detail-section">
              <div class="ms-bug-detail-section-header">
                <div>
                  <div class="ms-bug-detail-section-title">缺陷描述</div>
                </div>
                <div class="ms-bug-detail-toolbar-actions">
                  <el-button
                    v-if="canWrite && !descriptionEditing"
                    text
                    :icon="EditPen"
                    class="ms-bug-inline-action"
                    @click="startDescriptionEdit"
                  >
                    编辑内容
                  </el-button>
                  <template v-else-if="canWrite && descriptionEditing">
                    <el-button text @click="cancelDescriptionEdit">取消</el-button>
                    <el-button type="primary" :loading="descriptionSaving" @click="submitDescriptionEdit">保存</el-button>
                  </template>
                </div>
              </div>

              <div v-if="descriptionEditing" class="ms-bug-description-editor ms-bug-description-surface">
                <BugRichTextEditor
                  v-model="descriptionDraft"
                  :min-height="descriptionEditorMinHeight"
                  allow-inline-image
                  @add-inline-image="emit('add-inline-image', $event)"
                />
              </div>
              <div
                v-else
                class="ms-bug-rich-content ms-bug-description-surface"
                v-html="descriptionHtml"
                @click="handleRichContentClick"
              />
            </div>

            <div class="ms-bug-detail-section">
              <div class="ms-bug-detail-section-header">
                <div>
                  <div class="ms-bug-detail-section-title">附件</div>
                </div>
                <el-button
                  v-if="canWrite"
                  plain
                  class="ms-bug-inline-outline-action"
                  :icon="Paperclip"
                  :loading="attachmentUploading"
                  @click="requestUpload"
                >
                  上传附件
                </el-button>
              </div>

              <div class="ms-bug-attachment-surface">
                <div class="ms-bug-attachment-hint">支持所有文件类型，单个文件不超过 10MB</div>

                <div v-if="imageAttachments.length" class="ms-bug-attachment-group">
                  <div class="ms-bug-attachment-grid">
                    <div v-for="item in imageAttachments" :key="item.id" class="ms-bug-attachment-card">
                      <el-image
                        :src="getAuthorizedImageUrl(item.downloadUrl)"
                        :preview-src-list="previewUrls"
                        :initial-index="imageAttachments.findIndex(entry => entry.id === item.id)"
                        fit="cover"
                        class="ms-bug-attachment-image"
                      />
                      <div class="ms-bug-attachment-name">{{ item.fileName }}</div>
                      <div class="ms-bug-attachment-meta">{{ formatBugDateTime(item.createdAt) }}</div>
                      <div class="ms-bug-attachment-actions">
                        <el-button text type="primary" @click="emit('download-attachment', item.id)">下载</el-button>
                        <el-button
                          v-if="canWrite"
                          text
                          type="danger"
                          :loading="attachmentRemovingId === item.id"
                          @click="emit('remove-attachment', item.id)"
                        >
                          删除
                        </el-button>
                      </div>
                    </div>
                  </div>
                </div>

                <div v-if="fileAttachments.length" class="ms-bug-file-list">
                  <div v-for="item in fileAttachments" :key="item.id" class="ms-bug-file-row">
                    <div
                      class="ms-bug-file-icon"
                      :data-type="getAttachmentTypeLabel(item.fileName, item.contentType)"
                      :data-tone="getAttachmentTypeTone(item.fileName, item.contentType)"
                    >
                      <span class="ms-bug-file-icon-corner" />
                      <span class="ms-bug-file-icon-badge">
                        {{ getAttachmentTypeLabel(item.fileName, item.contentType) }}
                      </span>
                    </div>
                    <div class="ms-bug-file-main">
                      <div class="ms-bug-file-name">{{ item.fileName }}</div>
                      <div class="ms-bug-file-meta">
                        {{ formatAttachmentMeta(item.fileSize, item.uploadedByName, item.createdAt) }}
                      </div>
                    </div>
                    <div class="ms-bug-attachment-actions">
                      <el-button text type="primary" @click="emit('download-attachment', item.id)">下载</el-button>
                      <el-button
                        v-if="canWrite"
                        text
                        type="danger"
                        :loading="attachmentRemovingId === item.id"
                        @click="emit('remove-attachment', item.id)"
                      >
                        删除
                      </el-button>
                    </div>
                  </div>
                </div>

                <el-empty v-if="!detail.attachments.length" description="暂无附件" :image-size="64" />
              </div>
            </div>
          </section>

          <section v-show="activeTab === 'case'" class="ms-bug-detail-pane">
            <div class="ms-bug-detail-section">
              <div class="ms-bug-case-toolbar">
                <el-button v-if="canWrite" type="primary" plain @click="openAssociateCaseDialog">
                  关联用例
                </el-button>
                <el-input
                  v-model="caseKeyword"
                  clearable
                  placeholder="按用例编号或名称搜索"
                  class="ms-bug-case-search"
                />
              </div>

              <el-table
                v-if="caseRows.length"
                :data="caseRows"
                row-key="id"
                class="ms-bug-case-table"
              >
                <el-table-column prop="caseNo" label="用例编号" min-width="150">
                  <template #default="{ row }">
                    <el-button text type="primary" class="ms-bug-case-link" @click="openCase(row.id)">
                      {{ row.caseNo }}
                    </el-button>
                  </template>
                </el-table-column>
                <el-table-column prop="title" label="用例名称" min-width="260" show-overflow-tooltip />
                <el-table-column prop="workspaceName" label="所属项目" min-width="160" show-overflow-tooltip />
                <el-table-column prop="caseType" label="用例类型" width="120" />
                <el-table-column label="操作" width="120" fixed="right">
                  <template #default>
                    <el-button
                      v-if="canWrite"
                      text
                      type="danger"
                      :loading="associatingCase"
                      @click="emit('unlink-case')"
                    >
                      取消关联
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>

              <el-empty v-else description="暂无关联用例" :image-size="72" />
            </div>
          </section>

          <section v-show="activeTab === 'comment'" class="ms-bug-detail-pane">
            <div class="ms-bug-detail-section">
              <div class="ms-bug-detail-section-title">评论</div>
              <div v-if="detail.comments.length" class="ms-bug-comment-list">
                <div v-for="comment in detail.comments" :key="comment.id" class="ms-bug-comment-item">
                  <div class="ms-bug-comment-top">
                    <span class="ms-bug-comment-author">{{ comment.commenterName }}</span>
                    <span class="ms-bug-comment-time">{{ formatBugDateTime(comment.createdAt) }}</span>
                  </div>
                  <div class="ms-bug-comment-content">{{ comment.content }}</div>
                </div>
              </div>
              <el-empty v-else description="暂无评论" :image-size="64" />
            </div>

            <div class="ms-bug-detail-section">
              <div class="ms-bug-detail-section-title">发表评论</div>
              <div v-if="canWrite" class="ms-bug-comment-editor">
                <el-input
                  v-model="commentText"
                  type="textarea"
                  :rows="5"
                  placeholder="输入评论内容"
                />
                <div class="ms-bug-section-actions">
                  <el-button type="primary" :disabled="!commentText.trim()" :loading="commenting" @click="submitComment">
                    提交评论
                  </el-button>
                </div>
              </div>
              <el-empty v-else description="当前无编辑权限" :image-size="56" />
            </div>
          </section>

          <section v-show="activeTab === 'history'" class="ms-bug-detail-pane">
            <div class="ms-bug-detail-section">
              <div class="ms-bug-detail-section-title">变更历史</div>
              <div v-if="detail.activities.length" class="ms-bug-history-list">
                <div v-for="item in detail.activities" :key="item.id" class="ms-bug-history-item">
                  <div class="ms-bug-history-main">
                    <div class="ms-bug-history-title">{{ item.title || item.type }}</div>
                    <div class="ms-bug-history-detail">{{ formatActivityDetail(item) }}</div>
                  </div>
                  <div class="ms-bug-history-time">{{ formatBugDateTime(item.occurredAt) }}</div>
                </div>
              </div>
              <el-empty v-else description="暂无变更历史" :image-size="64" />
            </div>
          </section>
        </template>
      </div>
    </div>

    <BugCaseAssociateDialog
      v-model="associateDialogVisible"
      :workspace-code="detail?.workspaceCode || ''"
      :current-case-id="detail?.relatedCaseId ?? null"
      :associating="associatingCase"
      @associate="handleCaseAssociated"
    />

    <el-image-viewer
      v-if="richImagePreviewVisible"
      :url-list="richImagePreviewUrls"
      :initial-index="activeRichImagePreviewIndex"
      infinite
      hide-on-click-modal
      teleported
      @close="closeRichImagePreview"
      @switch="handleRichImagePreviewSwitch"
    />
  </el-drawer>
</template>

<style scoped>
.bug-hidden-input {
  display: none;
}

.ms-bug-detail-drawer :deep(.el-drawer__body) {
  padding: 0;
  overflow: hidden;
}

.ms-bug-detail-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
}

.ms-bug-detail-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px 0;
  flex: 0 0 auto;
}

.ms-bug-detail-title-wrap {
  min-width: 0;
  flex: 1;
}

.ms-bug-detail-object-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.ms-bug-detail-object-no {
  color: #175cd3;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
}

.ms-bug-detail-object-name {
  min-width: 0;
  color: #101828;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.5;
  word-break: break-word;
}

.ms-bug-detail-status-tag,
.ms-bug-inline-status-tag {
  --el-tag-border-color: #bfd7ff;
  --el-tag-text-color: #175cd3;
  --el-tag-bg-color: #eff8ff;
}

.ms-bug-detail-top-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
}

.ms-bug-detail-top-actions :deep(.el-button) {
  height: 30px;
  padding: 0 10px;
  border-radius: 6px;
  color: #344054;
  font-size: 13px;
}

.ms-bug-detail-top-actions :deep(.el-button:hover),
.ms-bug-detail-top-actions :deep(.el-button:focus-visible) {
  color: #175cd3;
  background: #eff8ff;
}

.ms-bug-detail-close {
  width: 30px;
  height: 30px;
  padding: 0;
}

:global(.ms-bug-detail-more-menu .el-dropdown-menu__item.is-danger) {
  color: #d92d20;
}

:global(.ms-bug-detail-more-menu .el-dropdown-menu__item.is-danger:hover) {
  background: #fef3f2;
  color: #b42318;
}

.ms-bug-detail-tabs {
  display: flex;
  align-items: center;
  gap: 28px;
  margin: 12px 20px 0;
  border-bottom: 1px solid #eaecf0;
  flex: 0 0 auto;
}

.ms-bug-detail-tab {
  position: relative;
  padding: 12px 0 11px;
  border: none;
  background: transparent;
  color: #667085;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.5;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.18s ease;
}

.ms-bug-detail-tab:hover,
.ms-bug-detail-tab:focus-visible {
  color: #175cd3;
}

.ms-bug-detail-tab.is-active {
  color: #101828;
  font-weight: 600;
}

.ms-bug-detail-tab.is-active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  border-radius: 999px 999px 0 0;
  background: #175cd3;
}

.ms-bug-detail-content {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: 18px 20px 22px;
}

.ms-bug-detail-pane {
  display: grid;
  gap: 24px;
}

.ms-bug-detail-section {
  display: grid;
  gap: 14px;
}

.ms-bug-detail-section-title {
  color: #344054;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.5;
}

.ms-bug-detail-section-header,
.ms-bug-comment-top,
.ms-bug-history-item,
.ms-bug-file-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.ms-bug-detail-section-header {
  padding-bottom: 4px;
  border-bottom: 1px solid #f2f4f7;
}

.ms-bug-section-tip,
.ms-bug-comment-time,
.ms-bug-history-time,
.ms-bug-file-meta,
.ms-bug-attachment-meta {
  color: #98a2b3;
  font-size: 12px;
  line-height: 1.6;
}

.ms-bug-basic-shell {
  display: grid;
  gap: 16px;
}

.ms-bug-basic-panel {
  display: grid;
  grid-template-columns: 1fr;
  gap: 18px;
  padding-top: 6px;
}

.ms-bug-basic-panel-meter {
  max-width: 560px;
}

.ms-bug-basic-row {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  min-height: 34px;
}

.ms-bug-basic-row-meter {
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 12px;
  min-height: 30px;
}

.ms-bug-basic-row-wide {
  grid-column: 1 / -1;
  align-items: flex-start;
}

.ms-bug-basic-control,
.ms-bug-basic-value-wrap {
  min-width: 0;
}

.ms-bug-basic-label {
  color: #1f2329;
  font-size: 14px;
  line-height: 30px;
  font-weight: 400;
}

.ms-bug-basic-label em {
  color: #f53f3f;
  font-style: normal;
}

.ms-bug-basic-panel-meter :deep(.el-select__wrapper) {
  min-height: 34px;
  border-radius: 2px;
  box-shadow: 0 0 0 1px #dcdfe6 inset;
  color: #1f2329;
  background: #fff;
  --el-text-color-regular: #1f2329;
  --el-text-color-placeholder: #a8abb2;
  --el-disabled-text-color: #1f2329;
}

.ms-bug-basic-panel-meter :deep(.el-select__selected-item),
.ms-bug-basic-panel-meter :deep(.el-select-selected__caret),
.ms-bug-basic-panel-meter :deep(.el-select__selection-text),
.ms-bug-basic-panel-meter :deep(.el-select__input),
.ms-bug-basic-panel-meter :deep(.el-select__input-wrapper),
.ms-bug-basic-panel-meter :deep(.el-select__selection),
.ms-bug-basic-panel-meter :deep(.el-select__placeholder.is-transparent) {
  color: #1f2329 !important;
}

.ms-bug-basic-panel-meter :deep(.el-select__placeholder:not(.is-transparent)),
.ms-bug-basic-panel-meter :deep(.el-select__placeholder:not(.is-transparent) span),
.ms-bug-basic-panel-meter :deep(.el-select__selected-item.el-select__placeholder:not(.is-transparent)),
.ms-bug-basic-panel-meter :deep(.el-select__selected-item.el-select__placeholder:not(.is-transparent) span) {
  color: #1f2329 !important;
}

.ms-bug-basic-panel-meter :deep(.el-select__placeholder.is-transparent),
.ms-bug-basic-panel-meter :deep(.el-select__placeholder.is-transparent) span,
.ms-bug-basic-panel-meter :deep(.el-select .el-select__selected-item.el-select__placeholder.is-transparent),
.ms-bug-basic-panel-meter :deep(.el-select .el-select__selected-item.el-select__placeholder.is-transparent) span {
  color: #a8abb2 !important;
}

.ms-bug-basic-panel-meter :deep(.is-disabled .el-select__wrapper),
.ms-bug-basic-panel-meter :deep(.is-disabled .el-select__selected-item),
.ms-bug-basic-panel-meter :deep(.is-disabled .el-select__placeholder:not(.is-transparent)),
.ms-bug-basic-panel-meter :deep(.is-disabled .el-select__placeholder:not(.is-transparent) span),
.ms-bug-basic-panel-meter :deep(.is-disabled .el-select__selection-text),
.ms-bug-basic-panel-meter :deep(.is-disabled .el-select__input),
.ms-bug-basic-panel-meter :deep(.is-disabled .el-select__placeholder) {
  color: #1f2329 !important;
}

.ms-bug-tag-select :deep(.el-select__wrapper) {
  align-items: center;
  padding: 0 10px;
}

.ms-bug-tag-select :deep(.el-select__selection) {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 32px;
}

.ms-bug-tag-select :deep(.el-select__selected-item) {
  margin: 0;
}

.ms-bug-tag-select :deep(.el-tag) {
  height: 24px;
  margin: 0;
  padding: 0 8px;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  background: #f5f7fa;
  color: #1f2329;
  line-height: 22px;
  box-shadow: none;
  --el-tag-text-color: #1f2329;
}

.ms-bug-tag-select :deep(.el-tag .el-tag__content) {
  color: #1f2329 !important;
  font-size: 12px;
  line-height: 22px;
}

.ms-bug-tag-select :deep(.el-tag .el-tag__close) {
  margin-left: 4px;
  color: #646a73;
}

.ms-bug-tag-select :deep(.el-select__input-wrapper) {
  margin: 0;
}

.ms-bug-tag-select :deep(.el-select__input) {
  min-width: 96px;
  margin: 0;
  color: #1f2329 !important;
  font-size: 12px;
  line-height: 24px;
}

.ms-bug-tag-select :deep(.el-select__placeholder) {
  color: #a8abb2 !important;
  font-size: 12px;
  line-height: 32px;
}

.ms-bug-tag-select :deep(.el-select__caret),
.ms-bug-tag-select :deep(.el-select__suffix) {
  display: none;
}

.ms-bug-transition-form,
.ms-bug-comment-editor {
  display: grid;
  gap: 12px;
}

.ms-bug-section-actions {
  display: flex;
  justify-content: flex-end;
}

.ms-bug-detail-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ms-bug-detail-toolbar-actions :deep(.el-button) {
  height: 28px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 400;
}

.ms-bug-inline-action {
  color: #175cd3;
}

.ms-bug-detail-toolbar-actions :deep(.ms-bug-inline-action:hover),
.ms-bug-detail-toolbar-actions :deep(.ms-bug-inline-action:focus-visible) {
  color: #175cd3;
  background: #eff8ff;
}

.ms-bug-inline-outline-action {
  border-color: #d0d5dd;
  color: #344054;
  background: #fff;
}

.ms-bug-detail-toolbar-actions :deep(.ms-bug-inline-outline-action:hover),
.ms-bug-detail-toolbar-actions :deep(.ms-bug-inline-outline-action:focus-visible) {
  border-color: #bfd7ff;
  color: #175cd3;
  background: #eff8ff;
}

.ms-bug-description-surface {
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fcfcfd;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.ms-bug-description-editor {
  overflow: hidden;
}

.ms-bug-rich-content {
  padding: 16px 18px;
  color: #344054;
  font-size: 13px;
  line-height: 1.8;
  word-break: break-word;
}

.ms-bug-rich-content :deep(h1),
.ms-bug-rich-content :deep(h2),
.ms-bug-rich-content :deep(h3),
.ms-bug-rich-content :deep(h4),
.ms-bug-rich-content :deep(h5),
.ms-bug-rich-content :deep(h6) {
  margin: 0 0 14px;
  color: #101828;
  line-height: 1.55;
}

.ms-bug-rich-content :deep(p),
.ms-bug-rich-content :deep(ul),
.ms-bug-rich-content :deep(ol) {
  margin: 0 0 14px;
}

.ms-bug-rich-content :deep(ul),
.ms-bug-rich-content :deep(ol) {
  padding-left: 20px;
}

.ms-bug-rich-content :deep(blockquote) {
  margin: 0 0 14px;
  padding: 8px 0 8px 12px;
  border-left: 3px solid #d0d5dd;
  color: #475467;
  background: rgba(248, 250, 252, 0.8);
}

.ms-bug-rich-content :deep(img) {
  display: block;
  max-width: 100%;
  max-height: 420px;
  margin: 10px 0 14px;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  object-fit: contain;
  cursor: zoom-in;
}

.ms-bug-rich-content :deep(.bug-drawer-empty-text) {
  margin: 0;
  color: #98a2b3;
}

.ms-bug-rich-content :deep(*:last-child) {
  margin-bottom: 0;
}

.ms-bug-attachment-surface {
  display: grid;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fcfcfd;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.6);
}

.ms-bug-attachment-hint {
  color: #98a2b3;
  font-size: 12px;
  line-height: 1.6;
}

.ms-bug-attachment-group,
.ms-bug-file-list,
.ms-bug-comment-list,
.ms-bug-history-list {
  display: grid;
  gap: 12px;
}

.ms-bug-attachment-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 12px;
}

.ms-bug-attachment-card {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid #eaecf0;
  border-radius: 8px;
  background: #fcfdff;
}

.ms-bug-attachment-image {
  width: 100%;
  height: 132px;
  overflow: hidden;
  border-radius: 8px;
  background: #f2f4f7;
}

.ms-bug-attachment-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.ms-bug-file-row {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
}

.ms-bug-file-icon {
  position: relative;
  display: inline-flex;
  align-items: flex-end;
  justify-content: center;
  width: 38px;
  height: 46px;
  padding: 0 0 6px;
  border: 1px solid var(--file-accent, #bfd7ff);
  border-radius: 8px;
  background: #fff;
  color: var(--file-accent-strong, #175cd3);
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.05);
  overflow: hidden;
}

.ms-bug-file-icon::after {
  content: '';
  position: absolute;
  left: 6px;
  right: 6px;
  bottom: 7px;
  height: 12px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--file-accent, #bfd7ff) 12%, white);
  pointer-events: none;
}

.ms-bug-file-icon-corner {
  position: absolute;
  top: -1px;
  right: -1px;
  width: 13px;
  height: 13px;
  background: color-mix(in srgb, var(--file-accent, #bfd7ff) 20%, white);
  clip-path: polygon(0 0, 100% 0, 100% 100%);
  border-top-right-radius: 8px;
  box-shadow: inset -1px 1px 0 rgba(255, 255, 255, 0.85);
}

.ms-bug-file-icon-badge {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 12px;
  padding: 0 2px;
  border-radius: 0;
  background: transparent;
  color: var(--file-accent-strong, #175cd3);
  font-size: 9px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0;
  text-transform: uppercase;
}

.ms-bug-file-icon[data-tone='pdf'] {
  --file-accent: #fecaca;
  --file-accent-strong: #dc2626;
}

.ms-bug-file-icon[data-tone='word'] {
  --file-accent: #bfdbfe;
  --file-accent-strong: #2563eb;
}

.ms-bug-file-icon[data-tone='excel'] {
  --file-accent: #bbf7d0;
  --file-accent-strong: #16a34a;
}

.ms-bug-file-icon[data-tone='ppt'] {
  --file-accent: #fed7aa;
  --file-accent-strong: #ea580c;
}

.ms-bug-file-icon[data-tone='text'] {
  --file-accent: #c7d7fe;
  --file-accent-strong: #315cec;
}

.ms-bug-file-icon[data-tone='code'] {
  --file-accent: #d9d6fe;
  --file-accent-strong: #7c3aed;
}

.ms-bug-file-icon[data-tone='archive'] {
  --file-accent: #fde68a;
  --file-accent-strong: #d97706;
}

.ms-bug-file-icon[data-tone='file'] {
  --file-accent: #d5d9e2;
  --file-accent-strong: #667085;
}

.ms-bug-file-row .ms-bug-attachment-actions {
  justify-content: flex-end;
  gap: 10px;
}

.ms-bug-comment-item,
.ms-bug-history-item {
  padding: 15px 0;
  border-top: 1px solid #f2f4f7;
}

.ms-bug-comment-item:first-child,
.ms-bug-history-item:first-child {
  border-top: none;
  padding-top: 0;
}

.ms-bug-file-main,
.ms-bug-comment-content,
.ms-bug-history-main {
  min-width: 0;
}

.ms-bug-file-main {
  display: grid;
  gap: 6px;
}

.ms-bug-comment-author,
.ms-bug-history-title,
.ms-bug-file-name,
.ms-bug-attachment-name {
  color: #344054;
  font-size: 13px;
  line-height: 1.65;
  font-weight: 600;
  word-break: break-word;
}

.ms-bug-comment-content,
.ms-bug-history-detail {
  margin-top: 8px;
  color: #475467;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.ms-bug-comment-time,
.ms-bug-history-time {
  flex: 0 0 auto;
  min-width: 124px;
  text-align: right;
  color: #b0b7c3;
  font-size: 11px;
  line-height: 1.4;
}

.ms-bug-file-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: #98a2b3;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-word;
}

.ms-bug-case-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ms-bug-case-search {
  width: min(280px, 100%);
}

.ms-bug-case-table :deep(.cell) {
  font-size: 13px;
}

.ms-bug-case-link {
  padding-left: 0;
  padding-right: 0;
}

@media (max-width: 900px) {
  .ms-bug-detail-topbar,
  .ms-bug-detail-section-header,
  .ms-bug-history-item,
  .ms-bug-case-toolbar {
    flex-direction: column;
  }

  .ms-bug-basic-row,
  .ms-bug-basic-row-meter {
    grid-template-columns: 1fr;
    gap: 6px;
  }

  .ms-bug-basic-actions {
    padding-left: 0;
  }

  .ms-bug-comment-time,
  .ms-bug-history-time {
    min-width: 0;
    text-align: left;
  }

  .ms-bug-file-row {
    grid-template-columns: 44px minmax(0, 1fr);
  }

  .ms-bug-file-row .ms-bug-attachment-actions {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }

  .ms-bug-case-search {
    width: 100%;
  }
}
</style>

