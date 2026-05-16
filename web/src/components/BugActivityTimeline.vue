<script setup lang="ts">
import { computed } from 'vue'
import type { BugActivity } from '../types/api'
import { formatBugDateTime, formatBugStatus } from '../utils/bugPresentation'

const props = defineProps<{
  activities: BugActivity[]
}>()

const timelineItems = computed(() => props.activities.map((item) => {
  const summary = formatActivitySummary(item)
  const detail = formatActivityDetail(item)
  return {
    ...item,
    summary,
    detail,
  }
}))

function formatActivitySummary(item: BugActivity) {
  if (item.title?.trim()) {
    return item.title.trim()
  }
  switch (item.type) {
    case 'CREATED':
      return `${item.operatorName || '未知用户'} 创建了缺陷`
    case 'ASSIGNED':
      return `${item.operatorName || '未知用户'} 更新了负责人`
    case 'STATUS_CHANGED':
      return `${item.operatorName || '未知用户'} 更新了状态`
    case 'COMMENT_ADDED':
      return `${item.operatorName || '未知用户'} 添加了评论`
    case 'ATTACHMENT_ADDED':
      return '上传了附件'
    case 'ATTACHMENT_REMOVED':
      return '删除了附件'
    case 'RELATION_UPDATED':
      return `${item.operatorName || '未知用户'} 更新了关联关系`
    default:
      return item.type
  }
}

function formatActivityDetail(item: BugActivity) {
  if (item.type === 'STATUS_CHANGED' || item.type === 'ASSIGNED') {
    const fromText = formatBugStatus(item.fromStatus)
    const toText = formatBugStatus(item.toStatus)
    const summary = item.fromStatus || item.toStatus ? `${fromText} -> ${toText}` : ''
    return [summary, item.content].filter(Boolean).join(' | ')
  }
  if (item.type === 'ATTACHMENT_ADDED' || item.type === 'ATTACHMENT_REMOVED') {
    return item.attachmentName || item.content || '-'
  }
  return item.content || '-'
}
</script>

<template>
  <section class="bug-detail-section bug-activity-section">
    <div class="bug-detail-section-header">
      <div>
        <div class="bug-detail-section-title">活动时间线</div>
        <div class="bug-detail-section-meta">按时间倒序展示缺陷处理过程</div>
      </div>
    </div>

    <div v-if="timelineItems.length" class="bug-activity-list">
      <div v-for="item in timelineItems" :key="item.id" class="bug-activity-item">
        <div class="bug-activity-dot" />
        <div class="bug-activity-main">
          <div class="bug-activity-summary">{{ item.summary }}</div>
          <div class="bug-activity-detail">{{ item.detail }}</div>
        </div>
        <div class="bug-activity-time">{{ formatBugDateTime(item.occurredAt) }}</div>
      </div>
    </div>
    <el-empty v-else description="暂无活动记录" :image-size="72" />
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

.bug-activity-list {
  display: grid;
  gap: 12px;
}

.bug-activity-item {
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: flex-start;
  padding: 14px 16px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fcfcfd;
}

.bug-activity-dot {
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: 999px;
  background: #409eff;
}

.bug-activity-main {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.bug-activity-summary {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.6;
  color: #344054;
}

.bug-activity-detail,
.bug-activity-time {
  font-size: 12px;
  line-height: 1.6;
  color: #667085;
  word-break: break-word;
}

@media (max-width: 900px) {
  .bug-activity-item {
    grid-template-columns: 12px minmax(0, 1fr);
  }

  .bug-activity-time {
    grid-column: 2;
  }
}
</style>
