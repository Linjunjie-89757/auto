<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Plus, Promotion, RefreshRight, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { platformApi } from '../api/platform'
import ListToolbar from '../components/ListToolbar.vue'
import TableSettingsDrawer from '../components/TableSettingsDrawer.vue'
import { useListToolbarState } from '../composables/useListToolbarState'
import type { TableSettingsColumn } from '../composables/useTableSettings'
import { useWorkspace } from '../composables/useWorkspace'
import type { BugDetail, BugStats, BugSummary, CreateBugPayload, UserItem, WorkspaceItem } from '../types/api'

const { workspaceCode, isAllScope } = useWorkspace()

const loading = ref(false)
const saving = ref(false)
const bugs = ref<BugSummary[]>([])
const users = ref<UserItem[]>([])
const workspaces = ref<WorkspaceItem[]>([])
const stats = ref<BugStats>({
  total: 0,
  todo: 0,
  inProgress: 0,
  pendingVerify: 0,
  closed: 0,
  rejected: 0,
})
const detail = ref<BugDetail | null>(null)
const drawerVisible = ref(false)
const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const caseBugVisible = ref(false)
const reportBugVisible = ref(false)
const commentText = ref('')
const transitionStatus = ref('')
const transitionComment = ref('')
const assignUserId = ref<number | null>(null)

const bugFilters = reactive({
  keyword: '',
  status: '',
  priority: '',
  severity: '',
  assigneeId: null as number | null,
  workspaceCode: '',
})

const bugFilterDefaults = {
  keyword: '',
  status: '',
  priority: '',
  severity: '',
  assigneeId: null as number | null,
  workspaceCode: '',
}

const bugStatusOptions = [
  { label: '待处理', value: 'TODO' },
  { label: '已指派', value: 'ASSIGNED' },
  { label: '处理中', value: 'IN_PROGRESS' },
  { label: '待验证', value: 'PENDING_VERIFY' },
  { label: '已关闭', value: 'CLOSED' },
  { label: '已拒绝', value: 'REJECTED' },
]

const priorityOptions = ['P0', 'P1', 'P2', 'P3']
const severityOptions = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

const emptyPayload = (): CreateBugPayload & { workspaceCode: string } => ({
  workspaceCode: workspaceCode.value === 'ALL' ? '' : workspaceCode.value,
  title: '',
  description: '',
  priority: 'P1',
  severity: 'HIGH',
  assigneeId: null,
  tags: [],
})

const formState = reactive<CreateBugPayload & { workspaceCode: string }>(emptyPayload())
const sourceBugState = reactive<CreateBugPayload & { workspaceCode: string; sourceId: number | null }>({
  ...emptyPayload(),
  sourceId: null,
})

const bugColumns: TableSettingsColumn[] = [
  { key: 'bugNo', label: '缺陷编号', required: true, defaultVisible: true },
  { key: 'title', label: '标题', required: true, defaultVisible: true },
  { key: 'priority', label: '优先级', defaultVisible: true },
  { key: 'severity', label: '严重程度', defaultVisible: true },
  { key: 'status', label: '状态', defaultVisible: true },
  { key: 'assigneeName', label: '负责人', defaultVisible: true },
  { key: 'workspaceName', label: '所属空间', defaultVisible: true, allOnly: true },
]

const bugListToolbar = useListToolbarState({
  tableSettingsKey: 'bug-table-settings-v1',
  filterStorageKey: 'bug-list-filters-v1',
  columns: bugColumns,
  filters: bugFilters,
  filterDefaults: bugFilterDefaults,
  isColumnAvailable: column => !column.allOnly || isAllScope.value,
})

const statCards = computed(() => [
  { label: '缺陷总数', value: stats.value.total },
  { label: '待处理', value: stats.value.todo },
  { label: '处理中', value: stats.value.inProgress },
  { label: '待验证', value: stats.value.pendingVerify },
])

const filteredBugs = computed(() => bugs.value.filter((item) => {
  const keyword = bugFilters.keyword.trim().toLowerCase()
  if (keyword) {
    const matched = item.bugNo.toLowerCase().includes(keyword) || item.title.toLowerCase().includes(keyword)
    if (!matched) {
      return false
    }
  }
  if (bugFilters.status && item.status !== bugFilters.status) {
    return false
  }
  if (bugFilters.priority && item.priority !== bugFilters.priority) {
    return false
  }
  if (bugFilters.severity && item.severity !== bugFilters.severity) {
    return false
  }
  if (bugFilters.assigneeId !== null) {
    const user = users.value.find(entry => entry.id === bugFilters.assigneeId)
    if (item.assigneeName !== (user?.displayName ?? '')) {
      return false
    }
  }
  if (isAllScope.value && bugFilters.workspaceCode && item.workspaceCode !== bugFilters.workspaceCode) {
    return false
  }
  return true
}))

function resetFilters() {
  bugListToolbar.filterMemory.reset()
}

async function loadBaseData() {
  loading.value = true
  try {
    const [bugPage, bugStats, userList, workspaceList] = await Promise.all([
      platformApi.getBugs(workspaceCode.value),
      platformApi.getBugStats(workspaceCode.value),
      platformApi.getUsers(),
      platformApi.getSwitchableWorkspaces(),
    ])
    bugs.value = bugPage.items
    stats.value = bugStats
    users.value = userList
    workspaces.value = workspaceList.filter(item => !item.allScope)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    loading.value = false
  }
}

async function openDetail(id: number) {
  try {
    detail.value = await platformApi.getBugDetail(workspaceCode.value, id)
    assignUserId.value = detail.value.assigneeId
    drawerVisible.value = true
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function openEditFromRow(id: number) {
  await openDetail(id)
  openEditDialog()
}

function openCreateDialog() {
  formMode.value = 'create'
  Object.assign(formState, emptyPayload())
  formVisible.value = true
}

function openEditDialog() {
  if (!detail.value) {
    return
  }
  formMode.value = 'edit'
  Object.assign(formState, {
    workspaceCode: detail.value.workspaceCode,
    title: detail.value.title,
    description: detail.value.description,
    priority: detail.value.priority,
    severity: detail.value.severity,
    assigneeId: detail.value.assigneeId,
    tags: [...detail.value.tags],
  })
  formVisible.value = true
}

async function submitBug() {
  if (isAllScope.value && !formState.workspaceCode) {
    ElMessage.error('全部空间视角下请先选择目标空间')
    return
  }
  saving.value = true
  try {
    const payload: CreateBugPayload = {
      workspaceCode: isAllScope.value ? formState.workspaceCode : undefined,
      title: formState.title,
      description: formState.description,
      priority: formState.priority,
      severity: formState.severity,
      assigneeId: formState.assigneeId,
      tags: formState.tags,
    }
    if (formMode.value === 'create') {
      await platformApi.createBug(workspaceCode.value, payload)
      ElMessage.success('缺陷创建成功')
    }
    else if (detail.value) {
      await platformApi.updateBug(workspaceCode.value, detail.value.id, payload)
      ElMessage.success('缺陷更新成功')
      await openDetail(detail.value.id)
    }
    formVisible.value = false
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function submitAssign() {
  if (!detail.value || !assignUserId.value) {
    return
  }
  try {
    detail.value = await platformApi.assignBug(workspaceCode.value, detail.value.id, assignUserId.value)
    ElMessage.success('负责人已更新')
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function submitTransition() {
  if (!detail.value || !transitionStatus.value) {
    return
  }
  try {
    detail.value = await platformApi.transitionBug(workspaceCode.value, detail.value.id, transitionStatus.value, transitionComment.value)
    transitionStatus.value = ''
    transitionComment.value = ''
    ElMessage.success('状态已流转')
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function submitComment() {
  if (!detail.value || !commentText.value.trim()) {
    return
  }
  try {
    await platformApi.addBugComment(workspaceCode.value, detail.value.id, commentText.value)
    commentText.value = ''
    detail.value = await platformApi.getBugDetail(workspaceCode.value, detail.value.id)
    ElMessage.success('评论已添加')
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

async function submitCaseBug() {
  if (!sourceBugState.sourceId) {
    return
  }
  saving.value = true
  try {
    await platformApi.createBugFromCase(workspaceCode.value, sourceBugState.sourceId, {
      workspaceCode: isAllScope.value ? sourceBugState.workspaceCode : undefined,
      title: sourceBugState.title,
      description: sourceBugState.description,
      priority: sourceBugState.priority,
      severity: sourceBugState.severity,
      assigneeId: sourceBugState.assigneeId,
      tags: sourceBugState.tags,
    })
    caseBugVisible.value = false
    ElMessage.success('已从用例创建缺陷')
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

async function submitReportBug() {
  if (!sourceBugState.sourceId) {
    return
  }
  saving.value = true
  try {
    await platformApi.createBugFromReport(workspaceCode.value, sourceBugState.sourceId, {
      workspaceCode: isAllScope.value ? sourceBugState.workspaceCode : undefined,
      title: sourceBugState.title,
      description: sourceBugState.description,
      priority: sourceBugState.priority,
      severity: sourceBugState.severity,
      assigneeId: sourceBugState.assigneeId,
      tags: sourceBugState.tags,
    })
    reportBugVisible.value = false
    ElMessage.success('已从报告创建缺陷')
    await loadBaseData()
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    saving.value = false
  }
}

watch(workspaceCode, () => {
  Object.assign(formState, emptyPayload())
  loadBaseData()
})

onMounted(() => {
  bugListToolbar.load()
  loadBaseData()
})
</script>

<template>
  <section class="page-shell">
    <div class="page-header">
      <div class="page-title">缺陷管理</div>
      <div class="page-actions">
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          新建缺陷
        </el-button>
      </div>
    </div>

    <div class="stats-grid">
      <article v-for="item in statCards" :key="item.label" class="metric-card">
        <div class="metric-label">{{ item.label }}</div>
        <div class="metric-value">{{ item.value }}</div>
        <div class="metric-trend">{{ isAllScope ? '全部空间汇总视角' : '当前空间缺陷概览' }}</div>
      </article>
    </div>

    <article class="panel-card">
      <div class="panel-header">
        <ListToolbar
          title="缺陷列表"
          show-settings
          @settings="bugListToolbar.settingsVisible.value = true"
        >
          <template #filters>
            <el-input
              v-model="bugFilters.keyword"
              placeholder="搜索编号或标题"
              clearable
              class="toolbar-filter-input"
            />
            <el-select v-model="bugFilters.status" clearable placeholder="状态" class="toolbar-filter-select">
              <el-option v-for="item in bugStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="bugFilters.priority" clearable placeholder="优先级" class="toolbar-filter-select">
              <el-option v-for="item in priorityOptions" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="bugFilters.severity" clearable placeholder="严重程度" class="toolbar-filter-select">
              <el-option v-for="item in severityOptions" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="bugFilters.assigneeId" clearable placeholder="负责人" class="toolbar-filter-select">
              <el-option v-for="item in users" :key="item.id" :label="item.displayName" :value="item.id" />
            </el-select>
            <el-select
              v-if="isAllScope"
              v-model="bugFilters.workspaceCode"
              clearable
              placeholder="所属空间"
              class="toolbar-filter-select"
            >
              <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
            </el-select>
            <el-button text @click="resetFilters">
              <el-icon><RefreshRight /></el-icon>
              重置
            </el-button>
          </template>
        </ListToolbar>
      </div>

      <el-table v-loading="loading" :data="filteredBugs" size="large">
        <template v-for="column in bugListToolbar.visibleColumns.value" :key="column.key">
          <el-table-column v-if="column.key === 'bugNo'" prop="bugNo" label="缺陷编号" width="170" />
          <el-table-column v-else-if="column.key === 'title'" prop="title" label="标题" min-width="260" />
          <el-table-column v-else-if="column.key === 'priority'" prop="priority" label="优先级" width="90" />
          <el-table-column v-else-if="column.key === 'severity'" prop="severity" label="严重程度" width="110" />
          <el-table-column v-else-if="column.key === 'status'" prop="status" label="状态" width="110" />
          <el-table-column v-else-if="column.key === 'assigneeName'" prop="assigneeName" label="负责人" width="120" />
          <el-table-column v-else-if="column.key === 'workspaceName'" prop="workspaceName" label="所属空间" width="130" />
        </template>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button text type="primary" @click="openDetail(row.id)">查看</el-button>
            <el-button text type="primary" @click="openEditFromRow(row.id)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </article>

    <TableSettingsDrawer
      v-model="bugListToolbar.settingsVisible.value"
      :columns="bugListToolbar.drawerColumns.value"
      :dragging-key="bugListToolbar.draggingColumnKey.value"
      @toggle-column="bugListToolbar.toggleColumnVisibility"
      @drag-start="bugListToolbar.handleDragStart"
      @drag-end="bugListToolbar.handleDragEnd"
      @drop-column="bugListToolbar.moveColumnToTarget"
      @reset="bugListToolbar.reset"
    />

    <el-dialog v-model="formVisible" :title="formMode === 'create' ? '新建缺陷' : '编辑缺陷'" width="640px">
      <el-form label-width="90px">
        <el-form-item v-if="isAllScope" label="目标空间" required>
          <el-select v-model="formState.workspaceCode" placeholder="请选择目标空间">
            <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="formState.title" />
        </el-form-item>
        <el-form-item label="描述" required>
          <el-input v-model="formState.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="formState.priority">
            <el-option v-for="item in priorityOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重程度">
          <el-select v-model="formState.severity">
            <el-option v-for="item in severityOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="formState.assigneeId" clearable>
            <el-option v-for="item in users" :key="item.id" :label="item.displayName" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitBug">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawerVisible" title="缺陷详情" size="720px">
      <template v-if="detail">
        <div class="detail-grid">
          <div class="detail-card">
            <div class="detail-title">{{ detail.title }}</div>
            <div class="detail-meta">{{ detail.bugNo }} | {{ detail.workspaceName }} | {{ detail.status }}</div>
            <p class="detail-body">{{ detail.description }}</p>
          </div>

          <div class="detail-card">
            <div class="detail-title">负责人调整</div>
            <div class="inline-form">
              <el-select v-model="assignUserId" placeholder="选择负责人">
                <el-option v-for="item in users" :key="item.id" :label="item.displayName" :value="item.id" />
              </el-select>
              <el-button type="primary" @click="submitAssign">
                <el-icon><User /></el-icon>
                指派
              </el-button>
            </div>
          </div>

          <div class="detail-card">
            <div class="detail-title">状态流转</div>
            <div class="inline-form">
              <el-select v-model="transitionStatus" placeholder="目标状态">
                <el-option v-for="item in bugStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-input v-model="transitionComment" placeholder="流转说明" />
              <el-button type="primary" @click="submitTransition">
                <el-icon><Promotion /></el-icon>
                流转
              </el-button>
            </div>
          </div>

          <div class="detail-card">
            <div class="detail-title">评论</div>
            <div class="inline-form">
              <el-input v-model="commentText" placeholder="输入评论内容" />
              <el-button type="primary" @click="submitComment">添加</el-button>
            </div>
            <div class="list-stack" style="margin-top: 12px;">
              <div v-for="item in detail.comments" :key="item.id" class="list-row">
                <div class="list-main">
                  <div class="list-title">{{ item.commenterName }}</div>
                  <div class="list-meta">{{ item.content }}</div>
                </div>
                <span class="status-pill status-neutral">{{ item.createdAt.slice(0, 16).replace('T', ' ') }}</span>
              </div>
            </div>
          </div>

          <div class="detail-card">
            <div class="detail-title">处理记录</div>
            <div class="list-stack">
              <div v-for="item in detail.flows" :key="item.id" class="list-row">
                <div class="list-main">
                  <div class="list-title">{{ item.operatorName }}：{{ item.fromStatus }} -> {{ item.toStatus }}</div>
                  <div class="list-meta">{{ item.actionComment }}</div>
                </div>
                <span class="status-pill status-neutral">{{ item.createdAt.slice(0, 16).replace('T', ' ') }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="caseBugVisible" title="从用例创建缺陷" width="620px">
      <el-form label-width="90px">
        <el-form-item v-if="isAllScope" label="目标空间" required>
          <el-select v-model="sourceBugState.workspaceCode">
            <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="sourceBugState.title" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="sourceBugState.description" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="caseBugVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCaseBug">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reportBugVisible" title="从报告创建缺陷" width="620px">
      <el-form label-width="90px">
        <el-form-item v-if="isAllScope" label="目标空间" required>
          <el-select v-model="sourceBugState.workspaceCode">
            <el-option v-for="item in workspaces" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="sourceBugState.title" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="sourceBugState.description" type="textarea" :rows="4" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportBugVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReportBug">提交</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.toolbar-filter-input {
  width: 200px;
}

.toolbar-filter-select {
  width: 136px;
}
</style>
