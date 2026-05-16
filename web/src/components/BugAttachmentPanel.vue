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
        <div v-for="item in fileAttachments" :key="item.id" class="bug-attachment-file-item">
          <div class="bug-attachment-file-main">
            <div class="bug-attachment-name">{{ item.fileName }}</div>
            <div class="bug-attachment-meta">
              {{ item.contentType || '未知类型' }} / {{ item.fileSize ?? 0 }} bytes / {{ formatBugDateTime(item.createdAt) }}
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

.bug-attachment-image-card,
.bug-attachment-file-item {
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

.bug-attachment-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
