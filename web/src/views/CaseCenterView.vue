<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const tabMeta: Record<string, { title: string }> = {
  manage: {
    title: '用例管理',
  },
  'ai-generate': {
    title: 'AI 用例生成',
  },
  'ai-records': {
    title: 'AI 生成用例记录',
  },
  'ai-config': {
    title: 'AI 配置',
  },
}

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

const activeMeta = computed(() => tabMeta[activeTab.value] ?? tabMeta.manage)
</script>

<template>
  <section class="page-shell case-center-shell">
    <div class="page-header">
      <div>
        <div class="page-title">{{ activeMeta.title }}</div>
      </div>
    </div>

    <div class="case-center-tabs">
      <el-tabs v-model="activeTab" class="case-tabs">
        <el-tab-pane label="用例管理" name="manage" />
        <el-tab-pane label="AI 用例生成" name="ai-generate" />
        <el-tab-pane label="AI 生成用例记录" name="ai-records" />
        <el-tab-pane label="AI 配置" name="ai-config" />
      </el-tabs>
    </div>

    <div class="case-center-content">
      <router-view />
    </div>
  </section>
</template>

<style scoped>
.case-center-shell {
  height: 100%;
  min-height: 0;
  overflow: visible;
}

.case-center-content {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.case-center-content > :deep(*) {
  height: 100%;
  min-height: 0;
}
</style>
