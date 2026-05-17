<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Folder, FolderOpened, Search } from '@element-plus/icons-vue'
import type { CaseDirectoryNode, CaseItem } from '../types/api'
import { platformApi } from '../api/platform'

type DirectoryTreeNode = {
  key: string
  id: number | null
  name: string
  selectable: boolean
  children: DirectoryTreeNode[]
}

const props = withDefaults(defineProps<{
  modelValue: boolean
  workspaceCode: string
  currentCaseId?: number | null
  associating?: boolean
}>(), {
  currentCaseId: null,
  associating: false,
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'associate', caseId: number): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const ROOT_KEY = 'root'
const directoryLoading = ref(false)
const caseLoading = ref(false)
const keyword = ref('')
const selectedDirectoryKey = ref(ROOT_KEY)
const selectedCaseId = ref<number | null>(null)
const directories = ref<CaseDirectoryNode[]>([])
const allCases = ref<CaseItem[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
let activeLoadToken = 0

const treeData = computed<DirectoryTreeNode[]>(() => {
  const mapNode = (node: CaseDirectoryNode): DirectoryTreeNode => ({
    key: `dir:${node.id}`,
    id: node.id,
    name: node.name,
    selectable: true,
    children: (node.children ?? []).map(mapNode),
  })
  return [{
    key: ROOT_KEY,
    id: null,
    name: '全部用例',
    selectable: true,
    children: (directories.value ?? []).map(mapNode),
  }]
})

const expandedKeys = computed(() => {
  const keys = [ROOT_KEY]
  const walk = (nodes: DirectoryTreeNode[]) => {
    nodes.forEach((node) => {
      if (node.children.length) {
        keys.push(node.key)
        walk(node.children)
      }
    })
  }
  walk(treeData.value)
  return keys
})

const selectedDirectoryId = computed(() => {
  if (selectedDirectoryKey.value === ROOT_KEY) {
    return null
  }
  const raw = selectedDirectoryKey.value.replace('dir:', '')
  const parsed = Number(raw)
  return Number.isFinite(parsed) ? parsed : null
})

const filteredCases = computed(() => {
  const lowered = keyword.value.trim().toLowerCase()
  if (!lowered) {
    return allCases.value
  }
  return allCases.value.filter(item =>
    item.caseNo.toLowerCase().includes(lowered)
    || item.title.toLowerCase().includes(lowered),
  )
})

const pagedCases = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredCases.value.slice(start, start + pageSize.value)
})

const selectedCase = computed(() => allCases.value.find(item => item.id === selectedCaseId.value) ?? null)
const canSubmit = computed(() => selectedCaseId.value != null && !props.associating)

watch(() => props.modelValue, async (value) => {
  if (!value) {
    return
  }
  keyword.value = ''
  currentPage.value = 1
  selectedDirectoryKey.value = ROOT_KEY
  selectedCaseId.value = props.currentCaseId ?? null
  await loadDirectories()
  await loadCases()
}, { immediate: true })

watch(() => props.workspaceCode, async () => {
  if (!props.modelValue) {
    return
  }
  selectedDirectoryKey.value = ROOT_KEY
  selectedCaseId.value = props.currentCaseId ?? null
  await loadDirectories()
  await loadCases()
})

watch(selectedDirectoryId, async () => {
  if (!props.modelValue) {
    return
  }
  currentPage.value = 1
  await loadCases()
})

watch(keyword, () => {
  currentPage.value = 1
})

async function loadDirectories() {
  if (!props.workspaceCode) {
    directories.value = []
    return
  }
  directoryLoading.value = true
  try {
    const workspaces = await platformApi.getCaseDirectories(props.workspaceCode)
    directories.value = workspaces.find(item => item.workspaceCode === props.workspaceCode)?.children ?? []
  }
  finally {
    directoryLoading.value = false
  }
}

async function loadCases() {
  if (!props.workspaceCode) {
    allCases.value = []
    return
  }
  caseLoading.value = true
  const token = ++activeLoadToken
  try {
    const firstPage = await platformApi.getCases(props.workspaceCode, {
      pageNo: 1,
      pageSize: 100,
      directoryId: selectedDirectoryId.value,
    })
    let items = [...firstPage.items]
    if (firstPage.totalPages > 1) {
      const restPages = await Promise.all(
        Array.from({ length: firstPage.totalPages - 1 }, (_, index) => platformApi.getCases(props.workspaceCode, {
          pageNo: index + 2,
          pageSize: 100,
          directoryId: selectedDirectoryId.value,
        })),
      )
      items = items.concat(restPages.flatMap(page => page.items))
    }
    if (token !== activeLoadToken) {
      return
    }
    allCases.value = items
    if (selectedCaseId.value != null && !items.some(item => item.id === selectedCaseId.value)) {
      selectedCaseId.value = null
    }
  }
  finally {
    if (token === activeLoadToken) {
      caseLoading.value = false
    }
  }
}

function handleTreeNodeClick(node: DirectoryTreeNode) {
  if (!node.selectable) {
    return
  }
  selectedDirectoryKey.value = node.key
}

function handleRowClick(row: CaseItem) {
  selectedCaseId.value = row.id
}

function submitAssociate() {
  if (selectedCaseId.value == null) {
    return
  }
  emit('associate', selectedCaseId.value)
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="&#x5173;&#x8054;&#x7528;&#x4F8B;"
    width="1120px"
    append-to-body
    destroy-on-close
    class="bug-case-associate-dialog"
  >
    <div class="bug-case-associate-shell">
      <aside class="bug-case-associate-sidebar" v-loading="directoryLoading">
        <el-tree
          :data="treeData"
          node-key="key"
          :default-expanded-keys="expandedKeys"
          :current-node-key="selectedDirectoryKey"
          highlight-current
          :expand-on-click-node="false"
          class="bug-case-associate-tree"
          @node-click="handleTreeNodeClick"
        >
          <template #default="{ data }">
            <div class="bug-case-associate-tree-node">
              <span class="bug-case-associate-tree-icon">
                <el-icon v-if="data.key === ROOT_KEY"><FolderOpened /></el-icon>
                <el-icon v-else-if="data.children?.length"><FolderOpened /></el-icon>
                <el-icon v-else><Folder /></el-icon>
              </span>
              <span class="bug-case-associate-tree-label">{{ data.name }}</span>
            </div>
          </template>
        </el-tree>
      </aside>

      <section class="bug-case-associate-main">
        <div class="bug-case-associate-toolbar">
          <el-input
            v-model="keyword"
            clearable
            :prefix-icon="Search"
            placeholder="&#x901A;&#x8FC7; ID &#x548C;&#x540D;&#x79F0;&#x641C;&#x7D22;"
            class="bug-case-associate-search"
          />
        </div>

        <div class="bug-case-associate-table-wrap" v-loading="caseLoading">
          <el-table
            :data="pagedCases"
            row-key="id"
            height="100%"
            highlight-current-row
            :current-row-key="selectedCaseId ?? undefined"
            empty-text="&#x6682;&#x65E0;&#x53EF;&#x5173;&#x8054;&#x7528;&#x4F8B;"
            class="bug-case-associate-table"
            @row-click="handleRowClick"
            @current-change="(row: CaseItem | null) => selectedCaseId = row?.id ?? null"
          >
            <el-table-column label="&#x7528;&#x4F8B;&#x7F16;&#x53F7;" min-width="180">
              <template #default="{ row }">
                <span class="bug-case-associate-case-no">{{ row.caseNo }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="&#x7528;&#x4F8B;&#x540D;&#x79F0;" min-width="320" show-overflow-tooltip />
            <el-table-column prop="workspaceName" label="&#x6240;&#x5C5E;&#x9879;&#x76EE;" min-width="180" show-overflow-tooltip />
            <el-table-column label="&#x7528;&#x4F8B;&#x7C7B;&#x578B;" width="140">
              <template #default>
                <span>&#x529F;&#x80FD;&#x7528;&#x4F8B;</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="bug-case-associate-pagination">
          <div class="bug-case-associate-selection">
            &#x5DF2;&#x9009;&#xFF1A;{{ selectedCase ? `${selectedCase.caseNo} ${selectedCase.title}` : '未选择用例' }}
          </div>
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            layout="total, sizes, prev, pager, next"
            :total="filteredCases.length"
            :page-sizes="[10, 20, 50]"
            small
          />
        </div>
      </section>
    </div>

    <template #footer>
      <div class="bug-case-associate-footer">
        <el-button @click="visible = false">&#x53D6;&#x6D88;</el-button>
        <el-button type="primary" :disabled="!canSubmit" :loading="associating" @click="submitAssociate">
          &#x5173;&#x8054;
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.bug-case-associate-shell {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
  min-height: 560px;
}

.bug-case-associate-sidebar,
.bug-case-associate-main {
  min-height: 0;
  border: 1px solid #eaecf0;
  border-radius: 8px;
  background: #fff;
}

.bug-case-associate-sidebar {
  overflow: auto;
  padding: 12px 8px;
}

.bug-case-associate-tree {
  min-height: 100%;
}

.bug-case-associate-tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.bug-case-associate-tree-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #d4a12a;
}

.bug-case-associate-tree-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bug-case-associate-main {
  display: flex;
  flex-direction: column;
  padding: 16px;
}

.bug-case-associate-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}

.bug-case-associate-search {
  width: 240px;
}

.bug-case-associate-table-wrap {
  flex: 1;
  min-height: 0;
}

.bug-case-associate-table {
  height: 100%;
}

.bug-case-associate-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.bug-case-associate-table :deep(th.el-table__cell) {
  background: #fff;
  color: #98a2b3;
  font-size: 12px;
  font-weight: 600;
}

.bug-case-associate-table :deep(.el-table__row) {
  cursor: pointer;
}

.bug-case-associate-table :deep(.el-table__row.current-row > td.el-table__cell) {
  background: #eff8ff;
}

.bug-case-associate-case-no {
  color: #175cd3;
  font-weight: 500;
}

.bug-case-associate-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 16px;
}

.bug-case-associate-selection {
  min-width: 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}

.bug-case-associate-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 960px) {
  .bug-case-associate-shell {
    grid-template-columns: 1fr;
  }

  .bug-case-associate-toolbar,
  .bug-case-associate-pagination {
    flex-direction: column;
    align-items: stretch;
  }

  .bug-case-associate-search {
    width: 100%;
  }
}
</style>
