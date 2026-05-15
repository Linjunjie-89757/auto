<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Promotion } from '@element-plus/icons-vue'
import type { BugDetail } from '../types/api'

type BugSourceContext = {
  caseNo: string
  caseTitle: string
  modulePath: string
  executionStatus: string
  actualResult: string
}

const props = withDefaults(defineProps<{
  modelValue: boolean
  detail: BugDetail | null
  loading?: boolean
  sourceContext?: BugSourceContext | null
  transitioning?: boolean
  commenting?: boolean
  attachmentUploading?: boolean
  attachmentRemovingId?: number | null
}>(), {
  loading: false,
  sourceContext: null,
  transitioning: false,
  commenting: false,
  attachmentUploading: false,
  attachmentRemovingId: null,
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'transition', payload: { status: string, comment: string }): void
  (event: 'comment', content: string): void
  (event: 'edit'): void
  (event: 'unlink'): void
  (event: 'upload-attachments', files: File[]): void
  (event: 'download-attachment', attachmentId: number): void
  (event: 'remove-attachment', attachmentId: number): void
}>()

const transitionStatus = ref('')
const transitionComment = ref('')
const commentText = ref('')
const uploadInput = ref<HTMLInputElement | null>(null)

const statusOptions = [
  { label: '待指派', value: 'TODO' },
  { label: '已指派', value: 'ASSIGNED' },
  { label: '处理中', value: 'IN_PROGRESS' },
  { label: '待验证', value: 'PENDING_VERIFY' },
  { label: '已关闭', value: 'CLOSED' },
  { label: '已拒绝', value: 'REJECTED' },
]

const selectableStatusOptions = computed(() => {
  const currentStatus = props.detail?.status
  return statusOptions.filter(item => item.value !== currentStatus)
})

const formattedDescription = computed(() => sanitizeRichHtml(props.detail?.description || '-'))

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) {
      transitionStatus.value = ''
      transitionComment.value = ''
      commentText.value = ''
    }
  },
)

function submitTransition() {
  if (!transitionStatus.value) {
    return
  }
  emit('transition', {
    status: transitionStatus.value,
    comment: transitionComment.value.trim(),
  })
}

function sanitizeRichHtml(content: string) {
  if (!content.trim()) {
    return '-'
  }
  const parser = new DOMParser()
  const doc = parser.parseFromString(`<div>${content}</div>`, 'text/html')
  const allowedTags = new Set([
    'DIV',
    'BR',
    'P',
    'SPAN',
    'STRONG',
    'B',
    'EM',
    'I',
    'U',
    'S',
    'MARK',
    'UL',
    'OL',
    'LI',
    'H1',
    'H2',
    'H3',
    'H4',
    'H5',
    'H6',
    'LABEL',
    'INPUT',
    'IMG',
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
        } else {
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
        if (attr.name !== 'src' || isSafeImageSrc(attr.value)) {
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

  return doc.body.innerHTML || '-'
}

function isSafeImageSrc(value: string) {
  return /^(https?:\/\/|\/api\/)/i.test(value)
}

function sanitizeStyle(value: string) {
  const rules = value
    .split(';')
    .map(item => item.trim())
    .filter(Boolean)
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

function submitComment() {
  const content = commentText.value.trim()
  if (!content) {
    return
  }
  emit('comment', content)
  commentText.value = ''
}

function triggerUpload() {
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
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="缺陷详情"
    size="760px"
    class="bug-detail-drawer"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="detail">
      <div v-loading="loading" class="bug-detail-layout">
        <section class="bug-detail-card">
          <div class="bug-detail-header">
            <div>
              <div class="bug-detail-title">{{ detail.title }}</div>
              <div class="bug-detail-meta">{{ detail.bugNo }} / {{ detail.workspaceName }}</div>
            </div>
            <div class="bug-detail-header-actions">
              <el-tag effect="plain">{{ detail.status }}</el-tag>
              <el-button text type="primary" @click="emit('edit')">编辑</el-button>
              <el-button text type="danger" @click="emit('unlink')">取消关联</el-button>
            </div>
          </div>
          <div class="bug-detail-summary-grid">
            <div class="bug-detail-summary-item">
              <div class="bug-detail-label">优先级</div>
              <div class="bug-detail-value">{{ detail.priority }}</div>
            </div>
            <div class="bug-detail-summary-item">
              <div class="bug-detail-label">严重程度</div>
              <div class="bug-detail-value">{{ detail.severity }}</div>
            </div>
            <div class="bug-detail-summary-item">
              <div class="bug-detail-label">负责人</div>
              <div class="bug-detail-value">{{ detail.assigneeName || '-' }}</div>
            </div>
            <div class="bug-detail-summary-item">
              <div class="bug-detail-label">来源类型</div>
              <div class="bug-detail-value">{{ detail.sourceType || '-' }}</div>
            </div>
            <div class="bug-detail-summary-item">
              <div class="bug-detail-label">创建人</div>
              <div class="bug-detail-value">{{ detail.reporterName || '-' }}</div>
            </div>
            <div class="bug-detail-summary-item">
              <div class="bug-detail-label">标签</div>
              <div class="bug-detail-value">{{ detail.tags.length ? detail.tags.join('、') : '-' }}</div>
            </div>
          </div>
          <div class="bug-detail-label">缺陷描述</div>
          <div class="bug-detail-description" v-html="formattedDescription"></div>
        </section>

        <section v-if="sourceContext" class="bug-detail-card">
          <div class="bug-detail-section-title">来源信息</div>
          <div class="bug-detail-summary-grid">
            <div class="bug-detail-summary-item">
              <div class="bug-detail-label">用例编号</div>
              <div class="bug-detail-value">{{ sourceContext.caseNo || '-' }}</div>
            </div>
            <div class="bug-detail-summary-item">
              <div class="bug-detail-label">执行结果</div>
              <div class="bug-detail-value">{{ sourceContext.executionStatus || '-' }}</div>
            </div>
            <div class="bug-detail-summary-item bug-detail-summary-item-full">
              <div class="bug-detail-label">用例标题</div>
              <div class="bug-detail-value">{{ sourceContext.caseTitle || '-' }}</div>
            </div>
            <div class="bug-detail-summary-item bug-detail-summary-item-full">
              <div class="bug-detail-label">用例模块</div>
              <div class="bug-detail-value">{{ sourceContext.modulePath || '-' }}</div>
            </div>
            <div class="bug-detail-summary-item bug-detail-summary-item-full">
              <div class="bug-detail-label">实际结果</div>
              <div class="bug-detail-value bug-detail-value-block">{{ sourceContext.actualResult || '-' }}</div>
            </div>
          </div>
        </section>

        <section class="bug-detail-card">
          <div class="bug-detail-header">
            <div class="bug-detail-section-title">附件</div>
            <el-button type="primary" plain size="small" :loading="attachmentUploading" @click="triggerUpload">
              上传附件
            </el-button>
          </div>
          <input
            ref="uploadInput"
            type="file"
            multiple
            style="display: none"
            @change="handleUploadChange"
          >
          <div v-if="detail.attachments.length" class="bug-detail-list">
            <div v-for="item in detail.attachments" :key="item.id" class="bug-detail-list-item">
              <div class="bug-detail-list-main">
                <div class="bug-detail-list-title">{{ item.fileName }}</div>
                <div class="bug-detail-list-text">{{ item.contentType || '未知类型' }} / {{ item.fileSize ?? 0 }} bytes</div>
              </div>
              <div class="bug-detail-list-actions">
                <el-button text type="primary" @click="emit('download-attachment', item.id)">下载</el-button>
                <el-button
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
          <el-empty v-else description="暂无附件" :image-size="68" />
        </section>

        <section class="bug-detail-card">
          <div class="bug-detail-section-title">状态流转</div>
          <div class="bug-detail-inline-form">
            <el-select v-model="transitionStatus" placeholder="选择目标状态">
              <el-option
                v-for="item in selectableStatusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-input v-model="transitionComment" placeholder="流转说明（选填）" />
            <el-button
              type="primary"
              :icon="Promotion"
              :loading="transitioning"
              :disabled="!transitionStatus"
              @click="submitTransition"
            >
              流转
            </el-button>
          </div>
        </section>

        <section class="bug-detail-card">
          <div class="bug-detail-section-title">评论</div>
          <div class="bug-detail-inline-form">
            <el-input v-model="commentText" placeholder="输入评论内容" />
            <el-button type="primary" :loading="commenting" :disabled="!commentText.trim()" @click="submitComment">
              添加评论
            </el-button>
          </div>
          <div v-if="detail.comments.length" class="bug-detail-list">
            <div v-for="item in detail.comments" :key="item.id" class="bug-detail-list-item">
              <div class="bug-detail-list-main">
                <div class="bug-detail-list-title">{{ item.commenterName }}</div>
                <div class="bug-detail-list-text">{{ item.content }}</div>
              </div>
              <div class="bug-detail-list-time">{{ item.createdAt?.slice(0, 16).replace('T', ' ') }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无评论" :image-size="68" />
        </section>

        <section class="bug-detail-card">
          <div class="bug-detail-section-title">处理记录</div>
          <div v-if="detail.flows.length" class="bug-detail-list">
            <div v-for="item in detail.flows" :key="item.id" class="bug-detail-list-item">
              <div class="bug-detail-list-main">
                <div class="bug-detail-list-title">{{ item.operatorName }}：{{ item.fromStatus }} -> {{ item.toStatus }}</div>
                <div class="bug-detail-list-text">{{ item.actionComment || '-' }}</div>
              </div>
              <div class="bug-detail-list-time">{{ item.createdAt?.slice(0, 16).replace('T', ' ') }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无处理记录" :image-size="68" />
        </section>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.bug-detail-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 18px 20px 0;
}

.bug-detail-drawer :deep(.el-drawer__body) {
  padding: 12px 20px 24px;
}

.bug-detail-layout {
  display: grid;
  gap: 16px;
}

.bug-detail-card {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #fff;
}

.bug-detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.bug-detail-header-actions,
.bug-detail-list-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.bug-detail-title,
.bug-detail-section-title {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.5;
  color: #344054;
}

.bug-detail-meta,
.bug-detail-label,
.bug-detail-list-time {
  font-size: 12px;
  line-height: 1.6;
  color: #667085;
}

.bug-detail-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
}

.bug-detail-summary-item {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.bug-detail-summary-item-full {
  grid-column: 1 / -1;
}

.bug-detail-value,
.bug-detail-description,
.bug-detail-list-title,
.bug-detail-list-text {
  font-size: 13px;
  line-height: 1.7;
  color: #344054;
  word-break: break-word;
}

.bug-detail-description,
.bug-detail-value-block,
.bug-detail-list-text {
  white-space: pre-wrap;
}

.bug-detail-description :deep(strong) {
  font-weight: 700;
}

.bug-detail-description :deep(h1),
.bug-detail-description :deep(h2),
.bug-detail-description :deep(h3),
.bug-detail-description :deep(h4),
.bug-detail-description :deep(h5),
.bug-detail-description :deep(h6) {
  margin: 0 0 8px;
  line-height: 1.45;
}

.bug-detail-description :deep(p),
.bug-detail-description :deep(ul),
.bug-detail-description :deep(ol) {
  margin: 0 0 14px;
}

.bug-detail-description :deep(mark) {
  padding: 0 2px;
  background: #fff3bf;
}

.bug-detail-description :deep(img) {
  display: block;
  max-width: 100%;
  max-height: 420px;
  margin: 10px 0 14px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  object-fit: contain;
}

.bug-detail-description :deep(ul[data-type="taskList"]) {
  list-style: none;
  padding-left: 0;
}

.bug-detail-description :deep(ul[data-type="taskList"] li) {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.bug-detail-description :deep(ul[data-type="taskList"] li > label) {
  margin-top: 2px;
}

.bug-detail-description :deep(ul[data-type="taskList"] li > div) {
  flex: 1;
}

.bug-detail-inline-form {
  display: grid;
  grid-template-columns: minmax(0, 180px) minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.bug-detail-list {
  display: grid;
  gap: 12px;
}

.bug-detail-list-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: #fcfcfd;
}

.bug-detail-list-main {
  display: grid;
  gap: 6px;
  min-width: 0;
}

@media (max-width: 1200px) {
  .bug-detail-summary-grid {
    grid-template-columns: 1fr;
  }

  .bug-detail-inline-form {
    grid-template-columns: 1fr;
  }

  .bug-detail-list-item {
    flex-direction: column;
  }

  .bug-detail-header-actions,
  .bug-detail-list-actions {
    flex-wrap: wrap;
  }
}
</style>
