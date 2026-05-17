<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, CopyDocument, Edit, Link, Paperclip, Promotion } from '@element-plus/icons-vue'
import type { BugDetail } from '../types/api'
import BugActivityTimeline from './BugActivityTimeline.vue'
import BugAttachmentPanel from './BugAttachmentPanel.vue'
import BugQuickActionsPanel from './BugQuickActionsPanel.vue'
import BugSourceContextCard from './BugSourceContextCard.vue'
import { formatBugDateTime, formatBugSeverity, formatBugSourceType, formatBugStatus } from '../utils/bugPresentation'

const props = withDefaults(defineProps<{
  detail: BugDetail
  mode?: 'page' | 'drawer'
  canWrite?: boolean
  transitioning?: boolean
  commenting?: boolean
  attachmentUploading?: boolean
  attachmentRemovingId?: number | null
  showBackAction?: boolean
  showOpenPageAction?: boolean
  shareUrl?: string
}>(), {
  mode: 'page',
  canWrite: false,
  transitioning: false,
  commenting: false,
  attachmentUploading: false,
  attachmentRemovingId: null,
  showBackAction: false,
  showOpenPageAction: false,
  shareUrl: '',
})

const emit = defineEmits<{
  (event: 'back'): void
  (event: 'edit'): void
  (event: 'open-page'): void
  (event: 'transition', payload: { status: string, comment: string }): void
  (event: 'comment', content: string): void
  (event: 'upload-attachments', files: File[]): void
  (event: 'download-attachment', attachmentId: number): void
  (event: 'remove-attachment', attachmentId: number): void
  (event: 'open-case', id: number): void
  (event: 'open-report', id: number): void
  (event: 'open-task', id: number): void
}>()

const uploadInput = ref<HTMLInputElement | null>(null)
const quickActionsRef = ref<HTMLElement | null>(null)
const descriptionHtml = computed(() => sanitizeRichHtml(props.detail.description || ''))
const basicInfoText = {
  workspace: '\u6240\u5c5e\u7a7a\u95f4',
  assignee: '\u5904\u7406\u4eba',
  reporter: '\u521b\u5efa\u4eba',
  createdAt: '\u521b\u5efa\u65f6\u95f4',
  updatedAt: '\u66f4\u65b0\u65f6\u95f4',
  tags: '\u6807\u7b7e',
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

async function copyLink() {
  try {
    await navigator.clipboard.writeText(props.shareUrl || window.location.href)
    ElMessage.success('链接已复制')
  }
  catch {
    ElMessage.error('链接复制失败')
  }
}

function scrollToQuickActions() {
  quickActionsRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function sanitizeRichHtml(content: string) {
  if (!content.trim()) {
    return '<p class="bug-detail-description-empty">暂无缺陷描述</p>'
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
          element.removeAttribute('style')
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

  return doc.body.innerHTML || '<p class="bug-detail-description-empty">暂无缺陷描述</p>'
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
  <div :class="['bug-detail-content', `bug-detail-content-${mode}`]">
    <input ref="uploadInput" type="file" multiple style="display: none" @change="handleUploadChange">

    <div class="bug-detail-main">
      <section class="bug-detail-section bug-detail-hero" data-bug-section="basic">
        <div class="bug-detail-hero-top">
          <div class="bug-detail-hero-main">
            <div class="bug-detail-hero-headline">
              <el-button v-if="showBackAction" text :icon="ArrowLeft" @click="emit('back')">返回列表</el-button>
              <span class="bug-detail-hero-code">{{ detail.bugNo }}</span>
              <el-tag effect="plain">{{ formatBugSourceType(detail.sourceType) }}</el-tag>
            </div>
            <h1 class="bug-detail-hero-title">{{ detail.title }}</h1>
            <div class="bug-detail-hero-tags">
              <el-tag effect="plain">{{ formatBugStatus(detail.status) }}</el-tag>
              <el-tag effect="plain">{{ detail.priority }}</el-tag>
              <el-tag effect="plain">{{ formatBugSeverity(detail.severity) }}</el-tag>
            </div>
          </div>

          <div class="bug-detail-hero-actions">
            <el-button text :icon="CopyDocument" @click="copyLink">复制链接</el-button>
            <el-button v-if="showOpenPageAction" text :icon="Link" @click="emit('open-page')">打开详情页</el-button>
            <el-button v-if="canWrite" text :icon="Paperclip" @click="requestUpload">上传附件</el-button>
            <el-button v-if="canWrite" text :icon="Promotion" @click="scrollToQuickActions">流转</el-button>
            <el-button text type="primary" :icon="Edit" @click="emit('edit')">编辑</el-button>
          </div>
        </div>

        <div class="bug-detail-basic-list">
          <div class="bug-detail-basic-row">
            <div class="bug-detail-label">{{ basicInfoText.workspace }}</div>
            <div class="bug-detail-value">{{ detail.workspaceName || '-' }}</div>
          </div>
          <div class="bug-detail-basic-row">
            <div class="bug-detail-label">{{ basicInfoText.assignee }}</div>
            <div class="bug-detail-value">{{ detail.assigneeName || '-' }}</div>
          </div>
          <div class="bug-detail-basic-row">
            <div class="bug-detail-label">{{ basicInfoText.reporter }}</div>
            <div class="bug-detail-value">{{ detail.reporterName || '-' }}</div>
          </div>
          <div class="bug-detail-basic-row">
            <div class="bug-detail-label">{{ basicInfoText.createdAt }}</div>
            <div class="bug-detail-value">{{ formatBugDateTime(detail.createdAt) }}</div>
          </div>
          <div class="bug-detail-basic-row">
            <div class="bug-detail-label">{{ basicInfoText.updatedAt }}</div>
            <div class="bug-detail-value">{{ formatBugDateTime(detail.updatedAt) }}</div>
          </div>
          <div class="bug-detail-basic-row bug-detail-basic-row-tags">
            <div class="bug-detail-label">{{ basicInfoText.tags }}</div>
            <div class="bug-detail-tag-list">
              <el-tag v-for="tag in detail.tags" :key="tag" effect="plain">{{ tag }}</el-tag>
              <span v-if="!detail.tags.length" class="bug-detail-empty-value">-</span>
            </div>
          </div>
        </div>
      </section>

      <section class="bug-detail-section" data-bug-section="detail">
        <div class="bug-detail-section-header">
          <div>
            <div class="bug-detail-section-title">缺陷描述</div>
            <div class="bug-detail-section-meta">富文本描述和关键信息</div>
          </div>
        </div>
        <div class="bug-detail-description" v-html="descriptionHtml" />
      </section>

      <div data-bug-section="case">
        <BugSourceContextCard
          :detail="detail"
          @open-case="emit('open-case', $event)"
          @open-report="emit('open-report', $event)"
          @open-task="emit('open-task', $event)"
        />
      </div>

      <div data-bug-section="attachment">
        <BugAttachmentPanel
          :attachments="detail.attachments"
          :can-write="canWrite"
          :attachment-uploading="attachmentUploading"
          :attachment-removing-id="attachmentRemovingId"
          @request-upload="requestUpload"
          @download="emit('download-attachment', $event)"
          @remove="emit('remove-attachment', $event)"
        />
      </div>

      <div data-bug-section="history">
        <BugActivityTimeline :activities="detail.activities" />
      </div>
    </div>

    <div ref="quickActionsRef" data-bug-section="comment">
      <BugQuickActionsPanel
        :detail="detail"
        :can-write="canWrite"
        :transitioning="transitioning"
        :commenting="commenting"
        @transition="emit('transition', $event)"
        @comment="emit('comment', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.bug-detail-content {
  display: grid;
  gap: 16px;
}

.bug-detail-content-page {
  grid-template-columns: minmax(0, 1fr) 320px;
  align-items: start;
}

.bug-detail-content-drawer {
  grid-template-columns: 1fr;
}

.bug-detail-main {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.bug-detail-section {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fff;
}

.bug-detail-section-header,
.bug-detail-hero-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.bug-detail-section-title {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.5;
  color: #344054;
}

.bug-detail-section-meta {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #667085;
}

.bug-detail-hero-main {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.bug-detail-hero-headline,
.bug-detail-hero-tags,
.bug-detail-hero-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.bug-detail-hero-code {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  color: #667085;
}

.bug-detail-hero-title {
  margin: 0;
  font-size: 22px;
  line-height: 1.4;
  color: #101828;
}

.bug-detail-basic-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  row-gap: 10px;
  column-gap: 24px;
}

.bug-detail-basic-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  align-items: start;
  gap: 12px;
  min-width: 0;
}

.bug-detail-label {
  font-size: 12px;
  line-height: 1.5;
  color: #667085;
}

.bug-detail-tag-list {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.bug-detail-empty-value {
  color: #98a2b3;
}

.bug-detail-value,
.bug-detail-description {
  font-size: 13px;
  line-height: 1.7;
  color: #344054;
  word-break: break-word;
}

.bug-detail-description :deep(p),
.bug-detail-description :deep(ul),
.bug-detail-description :deep(ol) {
  margin: 0 0 14px;
}

.bug-detail-description :deep(img) {
  display: block;
  max-width: 100%;
  max-height: 480px;
  margin: 10px 0 14px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  object-fit: contain;
}

.bug-detail-description :deep(.bug-detail-description-empty) {
  margin: 0;
  color: #667085;
}

@media (max-width: 1200px) {
  .bug-detail-content-page {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .bug-detail-basic-list {
    grid-template-columns: 1fr;
  }

  .bug-detail-basic-row {
    grid-template-columns: 72px minmax(0, 1fr);
  }

  .bug-detail-section-header,
  .bug-detail-hero-top {
    flex-direction: column;
  }
}
</style>


