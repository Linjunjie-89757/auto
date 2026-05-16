<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Close,
  CopyDocument,
  Edit,
  FullScreen,
  Link,
  MoreFilled,
  Paperclip,
} from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import type { BugActivity, BugDetail } from '../types/api'
import {
  formatBugDateTime,
  formatBugSeverity,
  formatBugSourceType,
  formatBugStatus,
  isImageFile,
} from '../utils/bugPresentation'
import { resolveApiUrl } from '../api/platform'

const props = withDefaults(defineProps<{
  modelValue: boolean
  detail: BugDetail | null
  loading?: boolean
  transitioning?: boolean
  commenting?: boolean
  attachmentUploading?: boolean
  attachmentRemovingId?: number | null
  canWrite?: boolean
}>(), {
  loading: false,
  transitioning: false,
  commenting: false,
  attachmentUploading: false,
  attachmentRemovingId: null,
  canWrite: false,
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'transition', payload: { status: string, comment: string }): void
  (event: 'comment', content: string): void
  (event: 'edit'): void
  (event: 'upload-attachments', files: File[]): void
  (event: 'download-attachment', attachmentId: number): void
  (event: 'remove-attachment', attachmentId: number): void
}>()

type DrawerTab = 'basic' | 'detail' | 'case' | 'comment' | 'history'

const router = useRouter()
const uploadInput = ref<HTMLInputElement | null>(null)
const activeTab = ref<DrawerTab>('detail')
const transitionStatus = ref('')
const transitionComment = ref('')
const commentText = ref('')

const statusOptions = [
  { label: '待处理', value: 'TODO' },
  { label: '已指派', value: 'ASSIGNED' },
  { label: '处理中', value: 'IN_PROGRESS' },
  { label: '待验证', value: 'PENDING_VERIFY' },
  { label: '已关闭', value: 'CLOSED' },
  { label: '已拒绝', value: 'REJECTED' },
]

const tabItems: Array<{ key: DrawerTab, label: string }> = [
  { key: 'basic', label: '基本信息' },
  { key: 'detail', label: '详情' },
  { key: 'case', label: '用例' },
  { key: 'comment', label: '评论' },
  { key: 'history', label: '变更历史' },
]

const selectableStatusOptions = computed(() => {
  if (!props.detail) {
    return statusOptions
  }
  return statusOptions.filter(item => item.value !== props.detail?.status)
})

const imageAttachments = computed(() => (
  props.detail?.attachments.filter(item => isImageFile(item.contentType, item.fileName)) ?? []
))

const fileAttachments = computed(() => (
  props.detail?.attachments.filter(item => !isImageFile(item.contentType, item.fileName)) ?? []
))

const previewUrls = computed(() => imageAttachments.value.map(item => resolveApiUrl(item.downloadUrl || '')))

const descriptionHtml = computed(() => sanitizeRichHtml(props.detail?.description || ''))

watch(() => props.modelValue, (value) => {
  if (value) {
    activeTab.value = 'detail'
  }
})

watch(() => props.detail?.id, () => {
  transitionStatus.value = ''
  transitionComment.value = ''
  commentText.value = ''
  activeTab.value = 'detail'
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

function openDetailPage() {
  if (!props.detail) {
    return
  }
  emit('update:modelValue', false)
  router.push({
    path: `/bugs/${props.detail.id}`,
    query: { workspace: props.detail.workspaceCode },
  })
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
  ElMessage.info('复制功能样式已预留，后续可接真实接口')
}

function deleteBugStylePlaceholder() {
  ElMessage.info('删除功能样式已预留，后续可接真实接口')
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

function openReport(id: number) {
  if (!props.detail) {
    return
  }
  router.push({
    path: '/automation/api',
    query: { workspace: props.detail.workspaceCode, reportId: String(id) },
  })
}

function openTask(id: number) {
  if (!props.detail) {
    return
  }
  router.push({
    path: '/automation/api',
    query: { workspace: props.detail.workspaceCode, taskId: String(id) },
  })
}

function submitTransition() {
  if (!transitionStatus.value) {
    return
  }
  emit('transition', {
    status: transitionStatus.value,
    comment: transitionComment.value.trim(),
  })
  transitionStatus.value = ''
  transitionComment.value = ''
}

function submitComment() {
  const content = commentText.value.trim()
  if (!content) {
    return
  }
  emit('comment', content)
  commentText.value = ''
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

function sanitizeRichHtml(content: string) {
  if (!content.trim()) {
    return '<p class="bug-drawer-empty-text">暂无缺陷内容</p>'
  }
  const parser = new DOMParser()
  const doc = parser.parseFromString(`<div>${content}</div>`, 'text/html')
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
      if (element.tagName === 'IMG' && ['src', 'alt', 'title'].includes(attr.name)) {
        if (attr.name !== 'src' || /^(https?:\/\/|\/api\/)/i.test(attr.value)) {
          return
        }
      }
      element.removeAttribute(attr.name)
    })
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
    :modal="false"
    :show-close="false"
    size="850px"
    class="ms-bug-detail-drawer"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="ms-bug-detail-shell">
      <input ref="uploadInput" type="file" multiple class="bug-hidden-input" @change="handleUploadChange">

      <div class="ms-bug-detail-topbar">
        <div class="ms-bug-detail-title-wrap">
          <div class="ms-bug-detail-title-line">
            <span class="ms-bug-detail-bugno">{{ detail?.bugNo || '-' }}</span>
            <span class="ms-bug-detail-title">{{ detail?.title || '缺陷详情' }}</span>
            <el-tag v-if="detail" effect="plain" size="small" class="ms-bug-detail-platform-tag">
              {{ detail.workspaceName }}
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
                <el-dropdown-item :icon="CopyDocument" @click="copyBugStylePlaceholder">复制</el-dropdown-item>
                <el-dropdown-item class="is-danger" @click="deleteBugStylePlaceholder">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button text :icon="FullScreen" @click="openDetailPage">全屏</el-button>
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
            <div class="ms-bug-detail-section">
              <div class="ms-bug-detail-section-title">基本信息</div>
              <div class="ms-bug-basic-grid">
                <div class="ms-bug-basic-item">
                  <span class="ms-bug-basic-label">状态</span>
                  <span class="ms-bug-basic-value">{{ formatBugStatus(detail.status) }}</span>
                </div>
                <div class="ms-bug-basic-item">
                  <span class="ms-bug-basic-label">优先级</span>
                  <span class="ms-bug-basic-value">{{ detail.priority }}</span>
                </div>
                <div class="ms-bug-basic-item">
                  <span class="ms-bug-basic-label">严重程度</span>
                  <span class="ms-bug-basic-value">{{ formatBugSeverity(detail.severity) }}</span>
                </div>
                <div class="ms-bug-basic-item">
                  <span class="ms-bug-basic-label">来源</span>
                  <span class="ms-bug-basic-value">{{ formatBugSourceType(detail.sourceType) }}</span>
                </div>
                <div class="ms-bug-basic-item">
                  <span class="ms-bug-basic-label">处理人</span>
                  <span class="ms-bug-basic-value">{{ detail.assigneeName || '-' }}</span>
                </div>
                <div class="ms-bug-basic-item">
                  <span class="ms-bug-basic-label">创建人</span>
                  <span class="ms-bug-basic-value">{{ detail.reporterName || '-' }}</span>
                </div>
                <div class="ms-bug-basic-item">
                  <span class="ms-bug-basic-label">创建时间</span>
                  <span class="ms-bug-basic-value">{{ formatBugDateTime(detail.createdAt) }}</span>
                </div>
                <div class="ms-bug-basic-item">
                  <span class="ms-bug-basic-label">更新时间</span>
                  <span class="ms-bug-basic-value">{{ formatBugDateTime(detail.updatedAt) }}</span>
                </div>
              </div>
            </div>

            <div class="ms-bug-detail-section">
              <div class="ms-bug-detail-section-title">标签</div>
              <div class="ms-bug-tag-list">
                <el-tag v-for="tag in detail.tags" :key="tag" effect="plain" size="small">{{ tag }}</el-tag>
                <span v-if="!detail.tags.length" class="ms-bug-empty-inline">暂无标签</span>
              </div>
            </div>

            <div class="ms-bug-detail-section">
              <div class="ms-bug-detail-section-title">状态流转</div>
              <div v-if="canWrite" class="ms-bug-transition-form">
                <el-select v-model="transitionStatus" placeholder="选择目标状态">
                  <el-option
                    v-for="item in selectableStatusOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
                <el-input
                  v-model="transitionComment"
                  type="textarea"
                  :rows="4"
                  placeholder="填写流转说明"
                />
                <div class="ms-bug-section-actions">
                  <el-button type="primary" :disabled="!transitionStatus" :loading="transitioning" @click="submitTransition">
                    更新状态
                  </el-button>
                </div>
              </div>
              <el-empty v-else description="当前账号仅可查看" :image-size="56" />
            </div>
          </section>

          <section v-show="activeTab === 'detail'" class="ms-bug-detail-pane">
            <div class="ms-bug-detail-section">
              <div class="ms-bug-detail-section-header">
                <div class="ms-bug-detail-section-title">缺陷内容</div>
                <el-button v-if="canWrite" text :icon="Edit" @click="emit('edit')">内容编辑</el-button>
              </div>
              <div class="ms-bug-rich-content" v-html="descriptionHtml" />
            </div>

            <div class="ms-bug-detail-section">
              <div class="ms-bug-detail-section-header">
                <div>
                  <div class="ms-bug-detail-section-title">添加附件</div>
                  <div class="ms-bug-section-tip">支持任意常见类型文件</div>
                </div>
                <el-button
                  v-if="canWrite"
                  plain
                  :icon="Paperclip"
                  :loading="attachmentUploading"
                  @click="requestUpload"
                >
                  添加附件
                </el-button>
              </div>

              <div v-if="imageAttachments.length" class="ms-bug-attachment-group">
                <div class="ms-bug-attachment-grid">
                  <div v-for="item in imageAttachments" :key="item.id" class="ms-bug-attachment-card">
                    <el-image
                      :src="resolveApiUrl(item.downloadUrl || '')"
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
                  <div class="ms-bug-file-main">
                    <div class="ms-bug-file-name">{{ item.fileName }}</div>
                    <div class="ms-bug-file-meta">
                      {{ item.contentType || '未知类型' }} · {{ item.fileSize ?? 0 }} bytes · {{ formatBugDateTime(item.createdAt) }}
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

              <el-empty v-if="!detail.attachments.length" description="暂无数据" :image-size="64" />
            </div>
          </section>

          <section v-show="activeTab === 'case'" class="ms-bug-detail-pane">
            <div class="ms-bug-detail-section">
              <div class="ms-bug-detail-section-title">关联信息</div>
              <div class="ms-bug-source-list">
                <div class="ms-bug-source-row">
                  <div class="ms-bug-source-label">来源类型</div>
                  <div class="ms-bug-source-body">
                    <div class="ms-bug-source-title">{{ formatBugSourceType(detail.sourceType) }}</div>
                    <div class="ms-bug-source-meta">{{ detail.reporterName }} · {{ formatBugDateTime(detail.createdAt) }}</div>
                  </div>
                </div>

                <div class="ms-bug-source-row">
                  <div class="ms-bug-source-label">关联用例</div>
                  <div class="ms-bug-source-body">
                    <template v-if="detail.sourceContext.caseSummary">
                      <div class="ms-bug-source-title">
                        {{ detail.sourceContext.caseSummary.caseNo }} {{ detail.sourceContext.caseSummary.title }}
                      </div>
                      <div class="ms-bug-source-meta">
                        {{ detail.sourceContext.caseSummary.modulePath || '-' }} · {{ detail.sourceContext.caseSummary.executionStatus }}
                      </div>
                      <el-button text type="primary" @click="openCase(detail.sourceContext.caseSummary.id)">打开用例</el-button>
                    </template>
                    <span v-else class="ms-bug-empty-inline">暂无关联</span>
                  </div>
                </div>

                <div class="ms-bug-source-row">
                  <div class="ms-bug-source-label">关联报告</div>
                  <div class="ms-bug-source-body">
                    <template v-if="detail.sourceContext.reportSummary">
                      <div class="ms-bug-source-title">{{ detail.sourceContext.reportSummary.reportName }}</div>
                      <div class="ms-bug-source-meta">
                        {{ detail.sourceContext.reportSummary.result }} · {{ detail.sourceContext.reportSummary.failureSummary || '-' }}
                      </div>
                      <el-button text type="primary" @click="openReport(detail.sourceContext.reportSummary.id)">打开报告</el-button>
                    </template>
                    <span v-else class="ms-bug-empty-inline">暂无关联</span>
                  </div>
                </div>

                <div class="ms-bug-source-row">
                  <div class="ms-bug-source-label">关联任务</div>
                  <div class="ms-bug-source-body">
                    <template v-if="detail.sourceContext.taskSummary">
                      <div class="ms-bug-source-title">{{ detail.sourceContext.taskSummary.taskName }}</div>
                      <div class="ms-bug-source-meta">
                        {{ detail.sourceContext.taskSummary.engineType }} · {{ detail.sourceContext.taskSummary.status }}
                      </div>
                      <el-button text type="primary" @click="openTask(detail.sourceContext.taskSummary.id)">打开任务</el-button>
                    </template>
                    <span v-else class="ms-bug-empty-inline">暂无关联</span>
                  </div>
                </div>
              </div>
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
              <el-empty v-else description="暂无数据" :image-size="64" />
            </div>

            <div class="ms-bug-detail-section">
              <div class="ms-bug-detail-section-title">添加评论</div>
              <div v-if="canWrite" class="ms-bug-comment-editor">
                <el-input
                  v-model="commentText"
                  type="textarea"
                  :rows="5"
                  placeholder="请输入评论内容"
                />
                <div class="ms-bug-section-actions">
                  <el-button type="primary" :disabled="!commentText.trim()" :loading="commenting" @click="submitComment">
                    发表评论
                  </el-button>
                </div>
              </div>
              <el-empty v-else description="当前账号仅可查看" :image-size="56" />
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
              <el-empty v-else description="暂无数据" :image-size="64" />
            </div>
          </section>
        </template>
      </div>
    </div>
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
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px 0;
  flex: 0 0 auto;
}

.ms-bug-detail-title-wrap {
  min-width: 0;
  flex: 1;
}

.ms-bug-detail-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.ms-bug-detail-bugno {
  color: #175cd3;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
}

.ms-bug-detail-title {
  min-width: 0;
  color: #101828;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.5;
  word-break: break-word;
}

.ms-bug-detail-platform-tag {
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
  gap: 24px;
  margin: 10px 20px 0;
  border-bottom: 1px solid #eaecf0;
  flex: 0 0 auto;
  overflow-x: auto;
}

.ms-bug-detail-tab {
  position: relative;
  padding: 11px 0 10px;
  border: none;
  background: transparent;
  color: #667085;
  font-size: 13px;
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
  padding: 14px 20px 20px;
  background: #fff;
}

.ms-bug-detail-pane {
  display: grid;
  gap: 18px;
}

.ms-bug-detail-section {
  display: grid;
  gap: 14px;
  padding-bottom: 2px;
}

.ms-bug-detail-section-header,
.ms-bug-comment-top,
.ms-bug-history-item,
.ms-bug-source-row,
.ms-bug-file-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.ms-bug-detail-section-title {
  color: #344054;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.5;
}

.ms-bug-section-tip,
.ms-bug-basic-label,
.ms-bug-comment-time,
.ms-bug-history-time,
.ms-bug-file-meta,
.ms-bug-source-meta,
.ms-bug-attachment-meta,
.ms-bug-empty-inline {
  color: #98a2b3;
  font-size: 12px;
  line-height: 1.6;
}

.ms-bug-basic-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
  padding-bottom: 2px;
}

.ms-bug-basic-item {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  min-height: 24px;
}

.ms-bug-basic-value,
.ms-bug-comment-author,
.ms-bug-history-title,
.ms-bug-source-title,
.ms-bug-file-name,
.ms-bug-attachment-name {
  color: #344054;
  font-size: 13px;
  line-height: 1.7;
  word-break: break-word;
}

.ms-bug-comment-author,
.ms-bug-history-title,
.ms-bug-source-title,
.ms-bug-file-name,
.ms-bug-attachment-name {
  font-weight: 600;
}

.ms-bug-tag-list {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding-top: 2px;
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

.ms-bug-rich-content {
  color: #475467;
  font-size: 13px;
  line-height: 1.8;
  word-break: break-word;
  padding: 0 0 6px;
}

.ms-bug-rich-content :deep(p),
.ms-bug-rich-content :deep(ul),
.ms-bug-rich-content :deep(ol) {
  margin: 0 0 14px;
}

.ms-bug-rich-content :deep(img) {
  display: block;
  max-width: 100%;
  max-height: 420px;
  margin: 10px 0 14px;
  border: 1px solid #e4e7ec;
  border-radius: 6px;
  object-fit: contain;
}

.ms-bug-rich-content :deep(.bug-drawer-empty-text) {
  margin: 0;
  color: #98a2b3;
}

.ms-bug-attachment-group,
.ms-bug-file-list,
.ms-bug-comment-list,
.ms-bug-history-list,
.ms-bug-source-list {
  display: grid;
  gap: 12px;
}

.ms-bug-attachment-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 12px;
  padding-top: 2px;
}

.ms-bug-attachment-card {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid #eaecf0;
  border-radius: 8px;
  background: #fcfdff;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.ms-bug-attachment-card:hover {
  border-color: #bfd7ff;
  box-shadow: 0 4px 12px rgba(23, 92, 211, 0.06);
  transform: translateY(-1px);
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

.ms-bug-attachment-actions :deep(.el-button),
.ms-bug-source-body :deep(.el-button) {
  padding-left: 0;
  padding-right: 0;
}

.ms-bug-file-row,
.ms-bug-comment-item,
.ms-bug-history-item,
.ms-bug-source-row {
  padding: 13px 0;
  border-top: 1px solid #f2f4f7;
}

.ms-bug-file-row:first-child,
.ms-bug-comment-item:first-child,
.ms-bug-history-item:first-child,
.ms-bug-source-row:first-child {
  border-top: none;
  padding-top: 0;
}

.ms-bug-file-row:last-child,
.ms-bug-comment-item:last-child,
.ms-bug-history-item:last-child,
.ms-bug-source-row:last-child {
  padding-bottom: 0;
}

.ms-bug-file-main,
.ms-bug-comment-content,
.ms-bug-history-main,
.ms-bug-source-body {
  min-width: 0;
}

.ms-bug-comment-content,
.ms-bug-history-detail {
  margin-top: 6px;
  color: #475467;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.ms-bug-source-label {
  flex: 0 0 88px;
  color: #98a2b3;
  font-size: 12px;
  line-height: 1.8;
}

.ms-bug-source-body {
  flex: 1;
}

.ms-bug-source-body :deep(.el-button:hover),
.ms-bug-attachment-actions :deep(.el-button:hover) {
  color: #175cd3;
}

@media (max-width: 900px) {
  .ms-bug-detail-topbar,
  .ms-bug-detail-section-header,
  .ms-bug-history-item,
  .ms-bug-source-row,
  .ms-bug-file-row {
    flex-direction: column;
  }

  .ms-bug-basic-grid {
    grid-template-columns: 1fr;
  }

  .ms-bug-basic-item {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .ms-bug-source-label {
    flex: none;
  }
}
</style>
