<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bell,
  ChatDotRound,
  Connection,
  DataBoard,
  DocumentChecked,
  Expand,
  Fold,
  FolderChecked,
  Monitor,
  Setting,
  SwitchButton,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { platformApi } from './api/platform'
import { navigationItems } from './data/platform'
import { useAuthStore } from './stores/auth'
import type { WorkspaceItem } from './types/api'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const workspaceOptions = ref<WorkspaceItem[]>([])
const isMenuCollapsed = ref(localStorage.getItem('app-menu-collapsed') === '1')

const iconMap = {
  dashboard: DataBoard,
  cases: DocumentChecked,
  bugs: ChatDotRound,
  api: Connection,
  web: Monitor,
  app: FolderChecked,
  settings: Setting,
}

const activeMenu = computed(() => {
  if (route.path.startsWith('/cases')) {
    return '/cases'
  }
  return route.path
})

const isPublicRoute = computed(() => route.meta.public === true)
const asideWidth = computed(() => (isMenuCollapsed.value ? '72px' : '248px'))

const currentWorkspace = computed(() => {
  const fromQuery = route.query.workspace?.toString()
  if (fromQuery && workspaceOptions.value.some(item => item.code === fromQuery)) {
    return fromQuery
  }
  const firstBusinessWorkspace = workspaceOptions.value.find(item => !item.allScope)?.code
  return firstBusinessWorkspace ?? 'ALL'
})

const currentUserName = computed(() => authStore.currentUser?.displayName ?? '')
const currentUserRole = computed(() => authStore.currentUser?.roleCode ?? '')
const currentUserInitials = computed(() =>
  (authStore.currentUser?.displayName ?? 'U').slice(0, 1).toUpperCase(),
)

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

async function handleLogout() {
  await authStore.logout()
  workspaceOptions.value = []
  ElMessage.success('已退出登录')
  router.replace('/login')
}

async function loadWorkspaces() {
  if (!authStore.isAuthenticated) {
    workspaceOptions.value = []
    return
  }

  try {
    workspaceOptions.value = await platformApi.getSwitchableWorkspaces()
    const currentQuery = route.query.workspace?.toString()
    if (!currentQuery || !workspaceOptions.value.some(item => item.code === currentQuery)) {
      const nextWorkspace = workspaceOptions.value.find(item => !item.allScope)?.code ?? 'ALL'
      router.replace({
        path: route.path,
        query: {
          ...route.query,
          workspace: nextWorkspace,
        },
      })
    }
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
}

watch(() => authStore.currentUser?.id, loadWorkspaces)
onMounted(loadWorkspaces)
</script>

<template>
  <router-view v-if="isPublicRoute" />

  <el-container v-else class="app-shell">
    <el-aside class="app-aside" :width="asideWidth">
      <div :class="['brand-block', { 'brand-block-collapsed': isMenuCollapsed }]">
        <div class="brand-mark">AT</div>
        <div v-if="!isMenuCollapsed">
          <div class="brand-title">Auto Test Hub</div>
          <div class="brand-subtitle">公司内部自动化测试平台</div>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        class="app-menu"
        router
        :collapse="isMenuCollapsed"
        :collapse-transition="false"
      >
        <el-menu-item
          v-for="item in navigationItems"
          :key="item.path"
          :index="item.path"
        >
          <el-icon><component :is="iconMap[item.icon]" /></el-icon>
          <template #title>
            <span>{{ item.label }}</span>
          </template>
        </el-menu-item>
      </el-menu>

      <div class="aside-footer">
        <div :class="['footer-card', { 'footer-card-collapsed': isMenuCollapsed }]">
          <template v-if="!isMenuCollapsed">
            <div class="footer-label">当前建设重点</div>
            <div class="footer-value">登录、空间权限、真实业务数据</div>
          </template>
          <el-button
            class="menu-collapse-button"
            circle
            :title="isMenuCollapsed ? '展开菜单' : '收起菜单'"
            @click="toggleMenuCollapse"
          >
            <el-icon>
              <component :is="isMenuCollapsed ? Expand : Fold" />
            </el-icon>
          </el-button>
        </div>
      </div>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div class="header-left">
          <el-select
            :model-value="currentWorkspace"
            class="workspace-switcher"
            placeholder="选择工作空间"
            @change="handleWorkspaceChange"
          >
            <el-option
              v-for="item in workspaceOptions"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
        </div>

        <div class="header-right">
          <el-button circle>
            <el-icon><Bell /></el-icon>
          </el-button>

          <el-dropdown>
            <span class="user-pill">
              <span class="user-avatar">{{ currentUserInitials }}</span>
              <span>{{ currentUserName }}</span>
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

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
