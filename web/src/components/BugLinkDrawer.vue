<script setup lang="ts">
import { computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { BugSummary } from '../types/api'

const props = withDefaults(defineProps<{
  modelValue: boolean
  bugs: BugSummary[]
  keyword: string
  loading?: boolean
  linkingBugId?: number | null
}>(), {
  loading: false,
  linkingBugId: null,
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'update:keyword', value: string): void
  (event: 'associate', bugId: number): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const keywordValue = computed({
  get: () => props.keyword,
  set: value => emit('update:keyword', value),
})
</script>

<template>
  <el-drawer
    v-model="visible"
    title="关联缺陷"
    size="920px"
    class="bug-link-drawer"
    destroy-on-close
  >
    <div class="bug-link-drawer-toolbar">
      <el-input
        v-model="keywordValue"
        placeholder="通过缺陷编号 / 标题搜索"
        clearable
        :prefix-icon="Search"
      />
    </div>

    <div class="bug-link-drawer-table">
      <el-table v-loading="loading" :data="bugs" size="large" height="100%" empty-text="暂无可关联缺陷">
        <el-table-column prop="bugNo" label="缺陷编号" width="170" />
        <el-table-column prop="title" label="标题" min-width="280" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column prop="severity" label="严重程度" width="110" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="负责人" width="120" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              text
              type="primary"
              :loading="linkingBugId === row.id"
              :disabled="linkingBugId !== null && linkingBugId !== row.id"
              @click="emit('associate', row.id)"
            >
              关联
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-drawer>
</template>

<style scoped>
.bug-link-drawer {
  --el-drawer-padding-primary: 20px;
}

.bug-link-drawer-toolbar {
  margin-bottom: 16px;
}

.bug-link-drawer-table {
  height: calc(100vh - 140px);
  min-height: 420px;
}
</style>
