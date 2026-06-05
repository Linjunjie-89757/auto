<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const activeTab = computed({
  get() {
    if (route.path.startsWith('/cases/ai-generate')) {
      return 'ai-generate'
    }
    if (route.path.startsWith('/cases/ai-records')) {
      return 'ai-records'
    }
    if (route.path.startsWith('/cases/ai-config')) {
      return 'ai-config'
    }
    return 'manage'
  },
  set(value: string) {
    const pathMap: Record<string, string> = {
      manage: '/cases/manage',
      'ai-generate': '/cases/ai-generate',
      'ai-records': '/cases/ai-records',
      'ai-config': '/cases/ai-config',
    }
    router.replace({
      path: pathMap[value] ?? '/cases/manage',
      query: route.query,
    })
  },
})
</script>

<template>
  <section class="page-shell case-center-shell">
    <div class="case-center-tabs">
      <el-tabs v-model="activeTab" class="case-tabs">
        <el-tab-pane label="用例管理" name="manage" />
        <el-tab-pane label="AI 用例生成" name="ai-generate" />
        <el-tab-pane label="AI 生成记录" name="ai-records" />
        <el-tab-pane label="AI 配置" name="ai-config" />
      </el-tabs>
    </div>

    <div class="case-center-content" :class="{ 'case-center-content-ai-config': activeTab === 'ai-config' }">
      <router-view />
    </div>
  </section>
</template>

<style scoped>
.case-center-shell {
  height: 100%;
  min-height: 0;
  gap: 0;
  overflow: hidden;
}

.case-center-tabs {
  display: flex;
  align-items: center;
  flex: 0 0 48px;
  width: 100%;
  border: 0;
  border-bottom: 1px solid #e5e7eb;
  border-radius: 0;
  background: #ffffff;
  box-shadow: none;
  padding: 0 24px;
}

.case-tabs {
  --el-color-primary: #111827;
  display: inline-flex;
  flex: 0 0 auto;
  border-radius: 8px;
  background: #f3f4f6;
  padding: 4px;
}

.case-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.case-tabs :deep(.el-tabs__nav-wrap) {
  min-height: 32px;
}

.case-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.case-tabs :deep(.el-tabs__active-bar) {
  display: none !important;
}

.case-tabs :deep(.el-tabs__nav) {
  gap: 2px;
}

.case-tabs :deep(.el-tabs__nav-scroll) {
  overflow: visible;
}

.case-tabs :deep(.el-tabs__item) {
  height: 32px;
  border: 0;
  border-radius: 6px;
  padding: 0 16px !important;
  color: #4b5563;
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
  white-space: nowrap;
}

.case-tabs :deep(.el-tabs__item:hover) {
  color: #374151;
}

.case-tabs :deep(.el-tabs__item.is-active) {
  background: #ffffff;
  color: #111827;
  font-weight: 500;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
}

.case-center-content {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: auto;
  padding: 24px;
}

.case-center-content > :deep(*) {
  min-height: 100%;
  min-width: 0;
}

.case-center-content-ai-config > :deep(*) {
  min-height: auto;
}
</style>
