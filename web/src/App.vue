<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { SwitchButton } from '@element-plus/icons-vue'
import { Bell, Bug, ChevronDown, ChevronLeft, ClipboardCheck, Layers, LayoutDashboard, Monitor, Network, Settings, Smartphone } from '@lucide/vue'
import { ElMessage } from 'element-plus'
import { platformApi } from './api/platform'
import { navigationItems } from './data/platform'
import { useAuthStore } from './stores/auth'
import type { WorkspaceItem } from './types/api'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const workspaceOptions = ref<WorkspaceItem[]>([])
const workspaceReady = ref(false)
const isMenuCollapsed = ref(localStorage.getItem('app-menu-collapsed') === '1')

const iconMap = {
  dashboard: LayoutDashboard,
  cases: ClipboardCheck,
  bugs: Bug,
  api: Network,
  web: Monitor,
  app: Smartphone,
  settings: Settings,
}

const activeMenu = computed(() => {
  if (route.path.startsWith('/cases')) {
    return '/cases'
  }
  return route.path
})

const isPublicRoute = computed(() => route.meta.public === true)
const asideWidth = computed(() => (isMenuCollapsed.value ? '80px' : '256px'))
const mainClass = computed(() => [
  'app-main',
  { 'app-main-workbench': route.path.startsWith('/automation/api') },
])

function resolveWorkspaceFallback() {
  return workspaceOptions.value.find(item => item.code === 'ALL')?.code
    ?? workspaceOptions.value.find(item => !item.allScope)?.code
    ?? workspaceOptions.value[0]?.code
    ?? 'ALL'
}

function isValidWorkspaceCode(value?: string | null) {
  return !!value && workspaceOptions.value.some(item => item.code === value)
}

async function ensureValidWorkspaceRoute() {
  const currentQuery = route.query.workspace?.toString()
  if (isValidWorkspaceCode(currentQuery)) {
    return
  }

  await router.replace({
    path: route.path,
    query: {
      ...route.query,
      workspace: resolveWorkspaceFallback(),
    },
  })
}

const currentWorkspace = computed(() => {
  const fromQuery = route.query.workspace?.toString()
  if (isValidWorkspaceCode(fromQuery)) {
    return fromQuery
  }
  return resolveWorkspaceFallback()
})
const currentWorkspaceLabel = computed(() =>
  workspaceOptions.value.find(item => item.code === currentWorkspace.value)?.name ?? '全部',
)

const currentUserName = computed(() => authStore.currentUser?.displayName ?? '')
const currentUserRole = computed(() => authStore.currentUser?.roleCode ?? '')
const currentUserInitials = computed(() =>
  (authStore.currentUser?.displayName ?? 'U').slice(0, 1).toUpperCase(),
)
const currentPageTitle = computed(() => {
  const path = route.path
  if (path.startsWith('/cases/manage/execute')) return '用例执行'
  if (path.startsWith('/cases/manage')) return '用例管理'
  if (path.startsWith('/cases/ai-generate')) return 'AI 用例生成'
  if (path.startsWith('/cases/ai-config')) return 'AI 配置'
  if (path.startsWith('/cases/ai-records')) return 'AI 生成记录'
  if (path.startsWith('/bugs')) return '缺陷管理'
  if (path.startsWith('/automation/api')) return '接口自动化'
  if (path.startsWith('/automation/web')) return 'Web UI 自动化'
  if (path.startsWith('/automation/app')) return 'APP 自动化'
  if (path.startsWith('/settings')) return '系统设置'
  if (path.startsWith('/dashboard')) return '工作台'
  return navigationItems.find(item => item.path === activeMenu.value)?.label ?? 'Auto Test Hub'
})

function toggleMenuCollapse() {
  isMenuCollapsed.value = !isMenuCollapsed.value
  localStorage.setItem('app-menu-collapsed', isMenuCollapsed.value ? '1' : '0')
}

function handleWorkspaceChange(value: string) {
  router.replace({
    path: route.path,
    query: {
      ...route.query,
      workspace: value,
    },
  })
}

function handleMenuSelect(path: string) {
  router.push({
    path,
    query: {
      workspace: currentWorkspace.value,
    },
  })
}

async function handleLogout() {
  await authStore.logout()
  workspaceOptions.value = []
  ElMessage.success('已退出登录')
  router.replace('/login')
}

async function loadWorkspaces() {
  workspaceReady.value = false

  if (isPublicRoute.value) {
    workspaceReady.value = true
    return
  }

  if (!authStore.isAuthenticated) {
    workspaceOptions.value = []
    workspaceReady.value = true
    return
  }

  try {
    workspaceOptions.value = await platformApi.getSwitchableWorkspaces()
    await ensureValidWorkspaceRoute()
    workspaceReady.value = true
  }
  catch (error) {
    workspaceReady.value = true
    ElMessage.error((error as Error).message)
  }
}

watch(() => authStore.currentUser?.id, loadWorkspaces)
watch(isPublicRoute, (publicRoute) => {
  if (!publicRoute) {
    void loadWorkspaces()
  }
})
watch(() => route.query.workspace, async () => {
  if (!authStore.isAuthenticated || workspaceOptions.value.length === 0) {
    return
  }
  workspaceReady.value = false
  await ensureValidWorkspaceRoute()
  workspaceReady.value = true
})
onMounted(loadWorkspaces)
</script>

<template>
  <router-view v-if="isPublicRoute" />

  <el-container v-else class="app-shell">
    <el-aside class="app-aside" :width="asideWidth">
      <button
        type="button"
        :class="['brand-block', { 'brand-block-collapsed': isMenuCollapsed }]"
        :title="isMenuCollapsed ? '展开菜单' : '收起菜单'"
        @click="toggleMenuCollapse"
      >
        <div class="brand-mark">AT</div>
        <div class="brand-copy">
          <div class="brand-title">Auto Test Hub</div>
          <div class="brand-subtitle">全功能自动化测试平台</div>
        </div>
        <ChevronLeft :class="['brand-collapse-icon', { 'is-collapsed': isMenuCollapsed }]" />
      </button>

      <nav class="app-menu" aria-label="主导航">
        <template
          v-for="item in navigationItems"
          :key="item.path"
        >
          <div v-if="item.path === '/settings'" class="app-menu-separator" />
          <div class="app-menu-item-wrap">
            <button
              type="button"
              :class="['app-menu-item', { 'is-active': activeMenu === item.path }]"
              @click="handleMenuSelect(item.path)"
            >
              <component :is="iconMap[item.icon]" class="app-menu-icon" />
              <span class="app-menu-label">{{ item.label }}</span>
            </button>
            <div v-if="isMenuCollapsed" class="app-menu-tooltip">
              {{ item.label }}
              <span class="app-menu-tooltip-arrow" />
            </div>
          </div>
        </template>
      </nav>

      <div class="aside-footer">
        <div class="footer-card">
          <div class="footer-user-row">
            <div class="footer-user-avatar">{{ currentUserInitials }}</div>
            <div class="footer-user-copy">
              <div class="footer-user-name">{{ currentUserName }}</div>
              <div class="footer-user-role">{{ currentUserRole }}</div>
            </div>
          </div>
          <div v-if="isMenuCollapsed" class="footer-tooltip">
            {{ currentUserName }}
            <span class="app-menu-tooltip-arrow" />
          </div>
        </div>
      </div>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <div class="header-page-title">{{ currentPageTitle }}</div>
          <span class="header-divider" />
          <el-dropdown
            trigger="click"
            popper-class="workspace-dropdown-menu"
            @command="handleWorkspaceChange"
          >
            <button class="workspace-switcher-button" type="button">
              <Layers class="workspace-switcher-icon" />
              <span class="workspace-switcher-label">{{ currentWorkspaceLabel }}</span>
              <ChevronDown class="workspace-switcher-caret" />
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="item in workspaceOptions"
                  :key="item.code"
                  :command="item.code"
                >
                  {{ item.name }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <div class="header-right">
          <button class="header-icon-button" type="button">
            <Bell class="header-bell-icon" />
            <span class="header-icon-dot" />
          </button>
          <span class="header-divider header-divider-right" />

          <el-dropdown>
            <span class="user-pill">
              <span class="user-avatar">{{ currentUserInitials }}</span>
              <span class="user-pill-name">{{ currentUserName }}</span>
              <ChevronDown class="user-pill-arrow" />
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ currentUserRole }}</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main v-if="workspaceReady" :class="mainClass">
        <router-view />
      </el-main>
      <el-main v-else :class="mainClass" />
    </el-container>
  </el-container>
</template>
