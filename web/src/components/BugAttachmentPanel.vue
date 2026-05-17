<script setup lang="ts">
import { computed } from 'vue'
import { resolveApiUrl } from '../api/platform'
import type { BugAttachment } from '../types/api'
import { formatBugDateTime, isImageFile } from '../utils/bugPresentation'

const props = withDefaults(defineProps<{
  attachments: BugAttachment[]
  canWrite?: boolean
  attachmentUploading?: boolean
  attachmentRemovingId?: number | null
}>(), {
  canWrite: false,
  attachmentUploading: false,
  attachmentRemovingId: null,
})

const emit = defineEmits<{
  (event: 'request-upload'): void
  (event: 'download', attachmentId: number): void
  (event: 'remove', attachmentId: number): void
}>()

const imageAttachments = computed(() => props.attachments.filter(item => isImageFile(item.contentType, item.fileName)))
const fileAttachments = computed(() => props.attachments.filter(item => !isImageFile(item.contentType, item.fileName)))
const previewUrls = computed(() => imageAttachments.value.map(item => resolveApiUrl(item.downloadUrl || '')))

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
    return 'doc'
  }
  if (['XLS', 'XLSX', 'CSV'].includes(label)) {
    return 'xls'
  }
  if (['PPT', 'PPTX'].includes(label)) {
    return 'ppt'
  }
  if (['PNG', 'JPG', 'JPEG', 'WEBP', 'GIF', 'BMP', 'SVG'].includes(label)) {
    return 'image'
  }
  if (['ZIP', 'RAR', '7Z'].includes(label)) {
    return 'zip'
  }
  return 'neutral'
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
</script>

<template>
  <section class="bug-detail-section">
    <div class="bug-detail-section-header">
      <div>
        <div class="bug-detail-section-title">证据附件</div>
        <div class="bug-detail-section-meta">查看截图、日志和其他缺陷证据</div>
      </div>
      <el-button v-if="canWrite" type="primary" plain size="small" :loading="attachmentUploading" @click="emit('request-upload')">
        上传附件
      </el-button>
    </div>

    <div v-if="imageAttachments.length" class="bug-attachment-group">
      <div class="bug-attachment-group-title">图片证据</div>
      <div class="bug-attachment-image-grid">
        <div v-for="item in imageAttachments" :key="item.id" class="bug-attachment-image-card">
          <el-image
            :src="resolveApiUrl(item.downloadUrl || '')"
            :preview-src-list="previewUrls"
            :initial-index="imageAttachments.findIndex(entry => entry.id === item.id)"
            fit="cover"
            class="bug-attachment-image"
          />
          <div class="bug-attachment-caption">
            <div class="bug-attachment-name">{{ item.fileName }}</div>
            <div class="bug-attachment-meta">{{ formatBugDateTime(item.createdAt) }}</div>
          </div>
          <div class="bug-attachment-actions">
            <el-button text type="primary" @click="emit('download', item.id)">下载</el-button>
            <el-button
              v-if="canWrite"
              text
              type="danger"
              :loading="attachmentRemovingId === item.id"
              @click="emit('remove', item.id)"
            >
              删除
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="fileAttachments.length" class="bug-attachment-group">
      <div class="bug-attachment-group-title">普通文件</div>
      <div class="bug-attachment-file-list">
        <div v-for="item in fileAttachments" :key="item.id" class="bug-attachment-file-row">
          <div
            class="bug-attachment-file-icon"
            :data-type="getAttachmentTypeLabel(item.fileName, item.contentType)"
            :data-tone="getAttachmentTypeTone(item.fileName, item.contentType)"
          >
            <span class="bug-attachment-file-icon-corner" />
            <span class="bug-attachment-file-icon-badge">
              {{ getAttachmentTypeLabel(item.fileName, item.contentType) }}
            </span>
          </div>
          <div class="bug-attachment-file-main">
            <div class="bug-attachment-name">{{ item.fileName }}</div>
            <div class="bug-attachment-meta">
              {{ formatAttachmentMeta(item.fileSize, item.uploadedByName, item.createdAt) }}
            </div>
            <div class="bug-attachment-status">
              <span class="bug-attachment-status-dot" />
              <span>上传成功</span>
            </div>
          </div>
          <div class="bug-attachment-actions">
            <el-button text type="primary" @click="emit('download', item.id)">下载</el-button>
            <el-button
              v-if="canWrite"
              text
              type="danger"
              :loading="attachmentRemovingId === item.id"
              @click="emit('remove', item.id)"
            >
              删除
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-if="!attachments.length" description="暂无附件" :image-size="72" />
  </section>
</template>

<style scoped>
.bug-detail-section {
  display: grid;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fff;
}

.bug-detail-section-header {
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

.bug-attachment-group {
  display: grid;
  gap: 12px;
}

.bug-attachment-group-title {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
  color: #667085;
}

.bug-attachment-image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 12px;
}

.bug-attachment-image-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fcfcfd;
}

.bug-attachment-image {
  width: 100%;
  height: 132px;
  border-radius: 8px;
  overflow: hidden;
  background: #f2f4f7;
}

.bug-attachment-caption,
.bug-attachment-file-main {
  display: grid;
  gap: 4px;
}

.bug-attachment-name {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.6;
  color: #344054;
  word-break: break-word;
}

.bug-attachment-meta {
  font-size: 12px;
  line-height: 1.6;
  color: #667085;
  word-break: break-word;
}

.bug-attachment-file-list {
  display: grid;
  gap: 12px;
}

.bug-attachment-file-row {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fff;
}

.bug-attachment-file-icon {
  position: relative;
  width: 38px;
  height: 46px;
  flex: 0 0 auto;
  border-radius: 8px;
  border: 1px solid var(--file-tone, #bfd7ff);
  background: #fff;
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.05);
}

.bug-attachment-file-icon::before {
  content: '';
  position: absolute;
  left: 6px;
  right: 6px;
  bottom: 7px;
  height: 12px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--file-tone, #bfd7ff) 12%, white);
}

.bug-attachment-file-icon-corner {
  position: absolute;
  top: -1px;
  right: -1px;
  width: 13px;
  height: 13px;
  background: color-mix(in srgb, var(--file-tone, #bfd7ff) 20%, white);
  clip-path: polygon(0 0, 100% 0, 100% 100%);
  border-top-right-radius: 8px;
  box-shadow: inset -1px 1px 0 rgba(255, 255, 255, 0.85);
}

.bug-attachment-file-icon-badge {
  position: absolute;
  left: 6px;
  right: 6px;
  bottom: 8px;
  display: block;
  border-radius: 0;
  padding: 0;
  font-size: 9px;
  line-height: 1.4;
  font-weight: 700;
  text-align: center;
  color: var(--file-tone-strong, #3b82f6);
  background: transparent;
}

.bug-attachment-file-icon[data-tone='pdf'] {
  --file-tone: #fecaca;
  --file-tone-strong: #f04438;
}

.bug-attachment-file-icon[data-tone='doc'] {
  --file-tone: #bfdbfe;
  --file-tone-strong: #2e90fa;
}

.bug-attachment-file-icon[data-tone='xls'] {
  --file-tone: #bbf7d0;
  --file-tone-strong: #12b76a;
}

.bug-attachment-file-icon[data-tone='ppt'] {
  --file-tone: #fed7aa;
  --file-tone-strong: #f79009;
}

.bug-attachment-file-icon[data-tone='image'] {
  --file-tone: #ddd6fe;
  --file-tone-strong: #7a5af8;
}

.bug-attachment-file-icon[data-tone='zip'] {
  --file-tone: #fde68a;
  --file-tone-strong: #6172f3;
}

.bug-attachment-file-icon[data-tone='neutral'] {
  --file-tone: #d5d9e2;
  --file-tone-strong: #98a2b3;
}

.bug-attachment-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: #12b76a;
}

.bug-attachment-status-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #12b76a;
  box-shadow: 0 0 0 4px rgba(18, 183, 106, 0.12);
}

.bug-attachment-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .bug-attachment-file-row {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .bug-attachment-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }
}
</style>
