<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight, Edit, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { platformApi } from '../api/platform'
import { useCaseCenterShared } from '../composables/useCaseCenterShared'
import { loadCaseExecutionContext, type CaseExecutionContext } from '../utils/caseExecutionContext'
import {
  executionStatusLabel,
  executionStatusTagClass,
  executionStatusTagType,
  formatDateTime,
  type ExecutionStatus,
} from '../utils/casePresentation'
import type { CaseDetail, CaseItem } from '../types/api'

const route = useRoute()
const router = useRouter()
const { workspaceCode, canWriteWorkspace, loadSharedBase, workspaces } = useCaseCenterShared()

const loading = ref(false)
const submitting = ref(false)
const activeTab = ref('detail')
const sidebarKeyword = ref('')
const autoNext = ref(false)
const contextState = ref<CaseExecutionContext | null>(null)
const executionCases = ref<CaseItem[]>([])
const detail = ref<CaseDetail | null>(null)
const selectedExecutionStatus = ref<ExecutionStatus | ''>('')
const executionComment = ref('')

const currentCaseId = computed(() => {
  const rawId = route.params.id?.toString()
  const parsed = Number(rawId)
  return Number.isFinite(parsed) ? parsed : null
})
const directWorkspaceCode = computed(() => route.query.workspace?.toString() ?? workspaceCode.value)
const effectiveWorkspaceCode = computed(() => contextState.value?.workspaceCode || directWorkspaceCode.value)
const visibleExecutionCases = computed(() => {
  const keyword = sidebarKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return executionCases.value
  }
  return executionCases.value.filter(item => (
    item.caseNo.toLowerCase().includes(keyword) || item.title.toLowerCase().includes(keyword)
  ))
})
const currentVisibleIndex = computed(() => visibleExecutionCases.value.findIndex(item => item.id === currentCaseId.value))
const activeCaseDisplayIndex = computed(() => (currentVisibleIndex.value >= 0 ? currentVisibleIndex.value + 1 : 0))
const canPreviewPreviousCase = computed(() => currentVisibleIndex.value > 0)
const canPreviewNextCase = computed(() => (
  currentVisibleIndex.value >= 0 && currentVisibleIndex.value < visibleExecutionCases.value.length - 1
))
const canEditCurrentCase = computed(() => !!detail.value && canWriteWorkspace(detail.value.workspaceCode))
const currentWorkspaceName = computed(() => (
  workspaces.value.find(item => item.code === effectiveWorkspaceCode.value)?.name || effectiveWorkspaceCode.value
))
const modulePath = computed(() => {
  if (!detail.value) {
    return '-'
  }
  const segments = [detail.value.workspaceName || currentWorkspaceName.value]
  if (detail.value.directoryName) {
    segments.push(detail.value.directoryName)
  }
  return segments.join(' / ')
})
const executionHistoryRows = computed(() => {
  if (!detail.value) {
    return []
  }
  if (!detail.value.executedAt && !detail.value.executorName && !detail.value.executionComment) {
    return []
  }
  return [{
    status: executionStatusLabel(detail.value.executionStatus),
    executorName: detail.value.executorName || '-',
    executedAt: formatDateTime(detail.value.executedAt),
    comment: detail.value.executionComment || '-',
  }]
})

function buildFallbackCase(detailRow: CaseDetail): CaseItem {
  return {
    id: detailRow.id,
    caseNo: detailRow.caseNo,
    title: detailRow.title,
    caseType: detailRow.caseType,
    priority: detailRow.priority,
    sourceType: detailRow.sourceType,
    status: detailRow.status,
    executionStatus: detailRow.executionStatus,
    ownerName: detailRow.ownerName,
    executorName: detailRow.executorName,
    executionComment: detailRow.executionComment,
    executedAt: detailRow.executedAt,
    workspaceCode: detailRow.workspaceCode,
    workspaceName: detailRow.workspaceName,
    directoryId: detailRow.directoryId,
    directoryName: detailRow.directoryName,
    createdBy: detailRow.createdBy,
    createdByName: detailRow.createdByName,
    createdAt: detailRow.createdAt,
    updatedBy: detailRow.updatedBy,
    updatedByName: detailRow.updatedByName,
    updatedAt: detailRow.updatedAt,
    reviewStatus: detailRow.reviewStatus,
    reviewComment: detailRow.reviewComment,
    reviewedBy: detailRow.reviewedBy,
    reviewedByName: detailRow.reviewedByName,
    reviewedAt: detailRow.reviewedAt,
  }
}

function syncExecutionInputs(target: CaseDetail | null) {
  executionComment.value = target?.executionComment ?? ''
  if (target?.executionStatus === 'PASSED' || target?.executionStatus === 'BLOCKED' || target?.executionStatus === 'FAILED') {
    selectedExecutionStatus.value = target.executionStatus
    return
  }
  selectedExecutionStatus.value = ''
}

function updateExecutionCollection(detailRow: CaseDetail) {
  const rowIndex = executionCases.value.findIndex(item => item.id === detailRow.id)
  if (rowIndex >= 0) {
    const current = executionCases.value[rowIndex]
    executionCases.value = executionCases.value.map(item => item.id === detailRow.id ? {
      ...current,
      ...detailRow,
    } : item)
    return
  }
  executionCases.value = [buildFallbackCase(detailRow)]
}

async function loadCaseDetail(caseId: number) {
  loading.value = true
  try {
    const detailRow = await platformApi.getCaseDetail(effectiveWorkspaceCode.value, caseId)
    detail.value = detailRow
    updateExecutionCollection(detailRow)
    syncExecutionInputs(detailRow)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    loading.value = false
  }
}

async function bootstrap() {
  await loadSharedBase()
  const savedContext = loadCaseExecutionContext()
  contextState.value = savedContext && (
    currentCaseId.value === null
    || savedContext.items.some(item => item.id === currentCaseId.value)
    || savedContext.workspaceCode === directWorkspaceCode.value
  )
    ? savedContext
    : null
  executionCases.value = contextState.value?.items ?? []
  if (currentCaseId.value !== null) {
    await loadCaseDetail(currentCaseId.value)
  }
}

function navigateToCase(caseId: number) {
  if (caseId === currentCaseId.value) {
    return
  }
  void router.replace({
    name: 'cases-execute',
    params: { id: caseId },
    query: route.query,
  })
}

function moveCase(offset: -1 | 1) {
  const nextRow = visibleExecutionCases.value[currentVisibleIndex.value + offset]
  if (!nextRow) {
    return
  }
  navigateToCase(nextRow.id)
}

function openCaseEdit() {
  if (!detail.value) {
    return
  }
  const query = {
    ...(contextState.value?.returnQuery ?? Object.fromEntries(
      Object.entries(route.query)
        .filter(([, value]) => typeof value === 'string')
        .map(([key, value]) => [key, value as string]),
    )),
    editCaseId: String(detail.value.id),
  }
  void router.push({
    name: 'cases-manage',
    query,
  })
}

async function submitExecution() {
  if (!detail.value || !currentCaseId.value) {
    return
  }
  if (!selectedExecutionStatus.value) {
    ElMessage.warning('请先选择执行结果')
    return
  }
  submitting.value = true
  try {
    const response = await platformApi.executeCase(effectiveWorkspaceCode.value, currentCaseId.value, {
      executionStatus: selectedExecutionStatus.value,
      executionComment: executionComment.value.trim(),
    })
    detail.value = response
    updateExecutionCollection(response)
    syncExecutionInputs(response)
    ElMessage.success('执行结果已更新')
    if (autoNext.value && canPreviewNextCase.value) {
      moveCase(1)
      return
    }
    if (autoNext.value && !canPreviewNextCase.value) {
      ElMessage.success('当前列表已经执行到最后一条')
    }
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    submitting.value = false
  }
}

watch(
  () => route.params.id,
  () => {
    if (currentCaseId.value !== null) {
      void loadCaseDetail(currentCaseId.value)
    }
  },
)

watch(visibleExecutionCases, (rows) => {
  if (!rows.length || currentCaseId.value === null) {
    return
  }
  if (!rows.some(item => item.id === currentCaseId.value)) {
    navigateToCase(rows[0].id)
  }
}, { flush: 'sync' })

onMounted(() => {
  void bootstrap()
})
</script>

<template>
  <section class="execution-page" v-loading="loading">
    <aside class="execution-sidebar">
      <div class="execution-sidebar-header">
        <div class="execution-sidebar-title">执行列表</div>
        <div class="execution-sidebar-meta">{{ contextState?.selectedNodePath || currentWorkspaceName }}</div>
      </div>
      <el-input
        v-model="sidebarKeyword"
        placeholder="通过 ID / 名称搜索"
        clearable
        :prefix-icon="Search"
        class="execution-sidebar-search"
      />
      <div class="execution-sidebar-list">
        <button
          v-for="item in visibleExecutionCases"
          :key="item.id"
          type="button"
          :class="['execution-sidebar-item', { 'is-active': item.id === currentCaseId }]"
          @click="navigateToCase(item.id)"
        >
          <div class="execution-sidebar-item-top">
            <span class="execution-sidebar-case-no">{{ item.caseNo }}</span>
            <el-tag
              size="small"
              effect="plain"
              :type="executionStatusTagType(item.executionStatus)"
              :class="executionStatusTagClass(item.executionStatus)"
            >
              {{ executionStatusLabel(item.executionStatus) }}
            </el-tag>
          </div>
          <div class="execution-sidebar-case-title">{{ item.title }}</div>
        </button>
        <el-empty v-if="!visibleExecutionCases.length" description="暂无匹配用例" :image-size="72" />
      </div>
      <div class="execution-sidebar-footer">
        <span>{{ executionCases.length }} 条</span>
        <span>{{ activeCaseDisplayIndex }}/{{ visibleExecutionCases.length || 0 }}</span>
      </div>
    </aside>

    <section class="execution-main">
      <div class="execution-main-header">
        <div class="execution-main-header-content">
          <div class="execution-main-header-top">
            <el-tag
              effect="plain"
              :type="executionStatusTagType(detail?.executionStatus || 'NOT_RUN')"
              :class="executionStatusTagClass(detail?.executionStatus || 'NOT_RUN')"
            >
              {{ executionStatusLabel(detail?.executionStatus || 'NOT_RUN') }}
            </el-tag>
          </div>
          <div class="execution-main-title">
            <span class="execution-main-case-no">{{ detail?.caseNo || '-' }}</span>
            <span>{{ detail?.title || '用例详情' }}</span>
          </div>
          <div class="execution-main-subtitle">{{ contextState?.sourceLabel || '当前执行视图' }}</div>
        </div>
        <el-button v-if="canEditCurrentCase" @click="openCaseEdit">
          <el-icon><Edit /></el-icon>
          编辑
        </el-button>
      </div>

      <div class="execution-body">
        <el-tabs v-model="activeTab" class="execution-tabs">
          <el-tab-pane label="基本信息" name="basic">
            <div class="execution-section-grid">
              <section class="execution-card">
                <div class="execution-card-label">用例模块</div>
                <div class="execution-card-value execution-card-value-muted">{{ modulePath }}</div>
              </section>
              <section class="execution-card">
                <div class="execution-card-label">优先级</div>
                <div class="execution-card-value">{{ detail?.priority || '-' }}</div>
              </section>
              <section class="execution-card">
                <div class="execution-card-label">创建人</div>
                <div class="execution-card-value">{{ detail?.createdByName || '-' }}</div>
              </section>
              <section class="execution-card">
                <div class="execution-card-label">创建时间</div>
                <div class="execution-card-value">{{ formatDateTime(detail?.createdAt || null) }}</div>
              </section>
              <section class="execution-card">
                <div class="execution-card-label">更新人</div>
                <div class="execution-card-value">{{ detail?.updatedByName || '-' }}</div>
              </section>
              <section class="execution-card">
                <div class="execution-card-label">更新时间</div>
                <div class="execution-card-value">{{ formatDateTime(detail?.updatedAt || null) }}</div>
              </section>
            </div>
          </el-tab-pane>

          <el-tab-pane label="详情" name="detail">
            <div class="execution-detail-stack">
              <section class="execution-detail-row">
                <div class="execution-detail-column">
                  <div class="execution-card-label">前置条件</div>
                  <div class="execution-rich-text execution-rich-text-tall">{{ detail?.precondition || '-' }}</div>
                </div>
                <div class="execution-detail-column">
                  <div class="execution-card-label">测试步骤</div>
                  <div class="execution-rich-text execution-rich-text-tall">{{ detail?.steps || '-' }}</div>
                </div>
                <div class="execution-detail-column">
                  <div class="execution-card-label">预期结果</div>
                  <div class="execution-rich-text execution-rich-text-tall">{{ detail?.expectedResult || '-' }}</div>
                </div>
                <div class="execution-detail-column">
                  <div class="execution-card-label">实际结果</div>
                  <el-input
                    v-model="executionComment"
                    type="textarea"
                    :rows="8"
                    resize="vertical"
                    placeholder="请输入实际结果"
                    class="execution-actual-result-input"
                  />
                </div>
              </section>
              <section class="execution-detail-card">
                <div class="execution-card-label">附件 / 截图</div>
                <el-empty description="附件功能将在后续版本开放" :image-size="84" />
              </section>
              <section class="execution-detail-card">
                <div class="execution-card-label">备注</div>
                <div class="execution-rich-text execution-note-placeholder">-</div>
              </section>
            </div>
          </el-tab-pane>

          <el-tab-pane label="缺陷列表" name="bugs">
            <section class="execution-detail-card">
              <div class="execution-tab-header">
                <div>
                  <div class="execution-card-title">关联缺陷</div>
                  <div class="execution-card-meta">V1 先预留缺陷位，后续补真实关联流程。</div>
                </div>
                <el-button disabled>添加缺陷</el-button>
              </div>
              <el-empty description="暂无关联缺陷" :image-size="84" />
            </section>
          </el-tab-pane>

          <el-tab-pane label="执行历史" name="history">
            <section class="execution-detail-card">
              <div class="execution-tab-header">
                <div>
                  <div class="execution-card-title">最近执行记录</div>
                  <div class="execution-card-meta">当前接口未返回完整历史列表，这里先展示最近一次执行信息。</div>
                </div>
              </div>
              <div v-if="executionHistoryRows.length" class="execution-history-list">
                <div v-for="row in executionHistoryRows" :key="`${row.executedAt}-${row.status}`" class="execution-history-item">
                  <div class="execution-history-top">
                    <span>{{ row.status }}</span>
                    <span>{{ row.executedAt }}</span>
                  </div>
                  <div class="execution-history-meta">执行人：{{ row.executorName }}</div>
                  <div class="execution-history-comment">{{ row.comment }}</div>
                </div>
              </div>
              <el-empty v-else description="暂无执行历史" :image-size="84" />
            </section>
          </el-tab-pane>
        </el-tabs>
      </div>

      <footer class="execution-footer">
        <div class="execution-footer-bar">
          <div class="execution-footer-nav">
            <el-button :disabled="!canPreviewPreviousCase" @click="moveCase(-1)">
              <el-icon><ArrowLeft /></el-icon>
              上一条
            </el-button>
            <div class="execution-footer-counter">{{ activeCaseDisplayIndex }}/{{ visibleExecutionCases.length || 0 }}</div>
            <el-button :disabled="!canPreviewNextCase" @click="moveCase(1)">
              下一条
              <el-icon><ArrowRight /></el-icon>
            </el-button>
            <div class="execution-auto-next">
              <span>自动下一条</span>
              <el-switch v-model="autoNext" />
            </div>
          </div>
          <div class="execution-submit-actions">
            <el-button disabled>添加缺陷</el-button>
            <el-button type="danger" plain :loading="submitting" @click="selectedExecutionStatus = 'FAILED'; submitExecution()">失败</el-button>
            <el-button type="primary" plain :loading="submitting" @click="selectedExecutionStatus = 'BLOCKED'; submitExecution()">阻塞</el-button>
            <el-button type="success" :loading="submitting" @click="selectedExecutionStatus = 'PASSED'; submitExecution()">通过</el-button>
          </div>
        </div>
      </footer>
    </section>
  </section>
</template>

<style scoped>
.execution-page {
  display: grid;
  grid-template-columns: 292px minmax(0, 1fr);
  gap: 16px;
  min-height: 720px;
}

.execution-sidebar,
.execution-main {
  min-height: 0;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #ffffff;
}

.execution-sidebar {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.execution-sidebar-header {
  padding: 18px 18px 12px;
  border-bottom: 1px solid var(--line-soft);
}

.execution-sidebar-title,
.execution-card-title {
  font-size: 14px;
  font-weight: 700;
  color: #344054;
}

.execution-sidebar-meta,
.execution-card-meta,
.execution-main-subtitle,
.execution-history-meta {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #667085;
}

.execution-sidebar-search {
  padding: 14px 18px 0;
}

.execution-sidebar-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 14px 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.execution-sidebar-item {
  width: 100%;
  padding: 14px 14px 12px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: #ffffff;
  text-align: left;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}

.execution-sidebar-item:hover,
.execution-sidebar-item.is-active {
  border-color: rgba(127, 86, 217, 0.4);
  background: #fcfaff;
  box-shadow: 0 0 0 1px rgba(127, 86, 217, 0.08);
}

.execution-sidebar-item-top,
.execution-main-header-top,
.execution-tab-header,
.execution-submit-main,
.execution-submit-tools,
.execution-history-top,
.execution-footer-nav,
.execution-submit-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.execution-sidebar-case-no,
.execution-main-case-no {
  color: #6941c6;
}

.execution-sidebar-case-title {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.6;
  color: #344054;
  word-break: break-word;
}

.execution-sidebar-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px 16px;
  border-top: 1px solid var(--line-soft);
  font-size: 12px;
  color: #667085;
}

.execution-main {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
}

.execution-main-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px 18px;
  border-bottom: 1px solid var(--line-soft);
}

.execution-main-header-content {
  min-width: 0;
}

.execution-main-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.5;
  color: #101828;
  word-break: break-word;
}

.execution-body {
  min-height: 0;
  overflow: auto;
  padding: 0 24px;
}

.execution-tabs {
  min-height: 100%;
}

.execution-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.execution-tabs :deep(.el-tabs__content) {
  padding: 20px 0 24px;
}

.execution-section-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.execution-card,
.execution-detail-card {
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #ffffff;
  padding: 16px;
}

.execution-card-label {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 600;
  color: #667085;
}

.execution-card-value {
  min-height: 44px;
  padding: 12px 14px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 10px;
  background: #f8fafc;
  font-size: 13px;
  line-height: 1.6;
  color: #344054;
}

.execution-card-value-muted,
.execution-rich-text,
.execution-history-comment {
  white-space: pre-wrap;
  word-break: break-word;
}

.execution-detail-stack {
  display: grid;
  gap: 14px;
}

.execution-detail-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.execution-detail-column,
.execution-detail-card {
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  background: #ffffff;
  padding: 16px;
}

.execution-rich-text {
  min-height: 84px;
  padding: 14px 16px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  border-radius: 10px;
  background: #f8fafc;
  font-size: 13px;
  line-height: 1.8;
  color: #344054;
}

.execution-rich-text-tall {
  min-height: 216px;
}

.execution-actual-result-input :deep(.el-textarea__inner) {
  min-height: 216px !important;
  border-radius: 10px;
  line-height: 1.7;
}

.execution-note-placeholder {
  min-height: 64px;
}

.execution-history-list {
  display: grid;
  gap: 12px;
}

.execution-history-item {
  padding: 14px 16px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: #fcfcfd;
}

.execution-history-top {
  align-items: flex-start;
  color: #344054;
  font-size: 13px;
  font-weight: 600;
}

.execution-history-comment {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.7;
  color: #475467;
}

.execution-footer {
  padding: 16px 24px 22px;
  border-top: 1px solid var(--line-soft);
  background: #ffffff;
}

.execution-footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.execution-footer-counter {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 64px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #ffffff;
  font-size: 13px;
  font-weight: 600;
  color: #344054;
}

.execution-auto-next {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #475467;
}

.execution-submit-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.status-tag-passed {
  --el-tag-text-color: #0f7a43;
  --el-tag-bg-color: rgba(15, 122, 67, 0.1);
  --el-tag-border-color: rgba(15, 122, 67, 0.22);
}

.status-tag-failed {
  --el-tag-text-color: #c53532;
  --el-tag-bg-color: rgba(197, 53, 50, 0.1);
  --el-tag-border-color: rgba(197, 53, 50, 0.22);
}

.status-tag-blocked {
  --el-tag-text-color: #6d42c7;
  --el-tag-bg-color: rgba(109, 66, 199, 0.1);
  --el-tag-border-color: rgba(109, 66, 199, 0.22);
}

.status-tag-pending {
  --el-tag-text-color: #667085;
  --el-tag-bg-color: rgba(102, 112, 133, 0.1);
  --el-tag-border-color: rgba(102, 112, 133, 0.22);
}

@media (max-width: 1280px) {
  .execution-page {
    grid-template-columns: 1fr;
  }

  .execution-section-grid {
    grid-template-columns: 1fr;
  }

  .execution-detail-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .execution-main-header,
  .execution-footer-bar,
  .execution-footer-nav {
    flex-direction: column;
    align-items: stretch;
  }

  .execution-detail-row {
    grid-template-columns: 1fr;
  }

  .execution-submit-actions {
    justify-content: stretch;
  }
}
</style>
