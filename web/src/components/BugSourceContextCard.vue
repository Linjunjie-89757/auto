<script setup lang="ts">
import type { BugDetail } from '../types/api'
import { formatBugDateTime, formatBugSourceType } from '../utils/bugPresentation'

const props = defineProps<{
  detail: BugDetail
}>()

const emit = defineEmits<{
  (event: 'open-case', id: number): void
  (event: 'open-report', id: number): void
  (event: 'open-task', id: number): void
}>()
</script>

<template>
  <section class="bug-detail-section">
    <div class="bug-detail-section-header">
      <div>
        <div class="bug-detail-section-title">来源与关联</div>
        <div class="bug-detail-section-meta">查看缺陷来源上下文和相关对象</div>
      </div>
    </div>

    <div class="bug-source-grid">
      <div class="bug-source-card">
        <div class="bug-source-card-title">来源类型</div>
        <div class="bug-source-card-value">{{ formatBugSourceType(detail.sourceType) }}</div>
        <div class="bug-source-card-meta">
          <template v-if="detail.sourceType === 'MANUAL'">
            {{ detail.reporterName }} / {{ formatBugDateTime(detail.createdAt) }}
          </template>
          <template v-else-if="detail.sourceContext.caseSummary">
            {{ detail.sourceContext.caseSummary.caseNo }} / {{ detail.sourceContext.caseSummary.executionStatus }}
          </template>
          <template v-else-if="detail.sourceContext.reportSummary">
            {{ detail.sourceContext.reportSummary.reportName }} / {{ detail.sourceContext.reportSummary.result }}
          </template>
          <template v-else>
            -
          </template>
        </div>
      </div>

      <div class="bug-source-card">
        <div class="bug-source-card-title">关联用例</div>
        <template v-if="detail.sourceContext.caseSummary">
          <div class="bug-source-card-value">{{ detail.sourceContext.caseSummary.title }}</div>
          <div class="bug-source-card-meta">
            {{ detail.sourceContext.caseSummary.caseNo }} / {{ detail.sourceContext.caseSummary.modulePath }}
          </div>
          <el-button text type="primary" @click="emit('open-case', detail.sourceContext.caseSummary.id)">打开用例</el-button>
        </template>
        <div v-else class="bug-source-card-empty">未关联</div>
      </div>

      <div class="bug-source-card">
        <div class="bug-source-card-title">关联报告</div>
        <template v-if="detail.sourceContext.reportSummary">
          <div class="bug-source-card-value">{{ detail.sourceContext.reportSummary.reportName }}</div>
          <div class="bug-source-card-meta">
            {{ detail.sourceContext.reportSummary.result }} / {{ detail.sourceContext.reportSummary.failureSummary || '-' }}
          </div>
          <el-button text type="primary" @click="emit('open-report', detail.sourceContext.reportSummary.id)">打开报告</el-button>
        </template>
        <div v-else class="bug-source-card-empty">未关联</div>
      </div>

      <div class="bug-source-card">
        <div class="bug-source-card-title">关联任务</div>
        <template v-if="detail.sourceContext.taskSummary">
          <div class="bug-source-card-value">{{ detail.sourceContext.taskSummary.taskName }}</div>
          <div class="bug-source-card-meta">
            {{ detail.sourceContext.taskSummary.engineType }} / {{ detail.sourceContext.taskSummary.status }}
          </div>
          <el-button text type="primary" @click="emit('open-task', detail.sourceContext.taskSummary.id)">打开任务</el-button>
        </template>
        <div v-else class="bug-source-card-empty">未关联</div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.bug-detail-section {
  display: grid;
  gap: var(--ath-space-3);
  padding: var(--ath-space-5);
  border: 1px solid var(--ath-border);
  border-radius: var(--ath-radius-lg);
  background: var(--ath-bg-panel);
  box-shadow: var(--ath-shadow-xs);
}

.bug-detail-section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--ath-space-3);
}

.bug-detail-section-title {
  color: var(--ath-text-main);
  font-size: var(--ath-font-sm);
  font-weight: var(--ath-weight-bold);
  line-height: var(--ath-line-sm);
}

.bug-detail-section-meta {
  margin-top: 4px;
  color: var(--ath-text-muted);
  font-size: var(--ath-font-xs);
  line-height: var(--ath-line-xs);
}

.bug-source-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--ath-space-3);
}

.bug-source-card {
  display: grid;
  gap: 6px;
  padding: var(--ath-space-3) var(--ath-space-4);
  border: 1px solid var(--ath-border);
  border-radius: var(--ath-radius-md);
  background: var(--ath-bg-subtle);
}

.bug-source-card-title {
  color: var(--ath-text-muted);
  font-size: var(--ath-font-xs);
  line-height: 1.5;
}

.bug-source-card-value {
  font-size: 13px;
  font-weight: var(--ath-weight-semibold);
  line-height: 1.6;
  color: var(--ath-text-main);
  word-break: break-word;
}

.bug-source-card-meta,
.bug-source-card-empty {
  color: var(--ath-text-muted);
  font-size: var(--ath-font-xs);
  line-height: 1.6;
  word-break: break-word;
}

.bug-source-card :deep(.el-button.is-text) {
  width: fit-content;
  height: 28px;
  margin-left: 0;
  padding: 0;
}

@media (max-width: 900px) {
  .bug-source-grid {
    grid-template-columns: 1fr;
  }
}
</style>
