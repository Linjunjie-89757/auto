<script setup lang="ts">
import { Rank } from '@element-plus/icons-vue'

type DrawerColumn = {
  key: string
  label: string
  required?: boolean
  visible: boolean
  draggable?: boolean
}

withDefaults(defineProps<{
  modelValue: boolean
  title?: string
  columns: DrawerColumn[]
  pageSizeEnabled?: boolean
  pageSize?: number
  pageSizeOptions?: number[]
  draggingKey?: string | null
}>(), {
  title: '表格设置',
  pageSizeEnabled: false,
  pageSize: 10,
  pageSizeOptions: () => [10, 20, 30, 40, 50],
  draggingKey: null,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  pageSizeChange: [size: number]
  toggleColumn: [key: string, value: boolean | string | number]
  dragStart: [key: string]
  dragEnd: []
  dropColumn: [targetKey: string]
  reset: []
}>()
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    size="420px"
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <div v-if="pageSizeEnabled" class="settings-section settings-section-compact">
      <div class="settings-title">每页显示数量</div>
      <div class="page-size-segment" role="radiogroup" aria-label="每页显示数量">
        <button
          v-for="size in pageSizeOptions"
          :key="size"
          type="button"
          :class="['page-size-option', { 'page-size-option-active': pageSize === size }]"
          :aria-pressed="pageSize === size"
          @click="emit('pageSizeChange', size)"
        >
          {{ size }}条
        </button>
      </div>
    </div>

    <div class="settings-section">
      <div class="settings-header">
        <div class="settings-title">表头设置</div>
        <el-button text @click="emit('reset')">恢复默认</el-button>
      </div>
      <div class="settings-note">支持显示控制、拖拽排序和本地记忆</div>
      <div class="settings-list">
        <div
          v-for="column in columns"
          :key="column.key"
          :class="['settings-item', { 'settings-item-dragging': draggingKey === column.key }]"
          :data-column-key="column.key"
          :draggable="column.draggable"
          @dragstart="emit('dragStart', column.key)"
          @dragend="emit('dragEnd')"
          @dragover.prevent
          @drop.prevent="emit('dropColumn', column.key)"
        >
          <div class="settings-item-main">
            <div class="settings-item-label">
              <el-icon v-if="column.draggable" class="settings-drag-handle"><Rank /></el-icon>
              <span>{{ column.label }}</span>
              <span v-if="column.required" class="settings-required">必显</span>
            </div>
          </div>
          <el-switch
            :model-value="column.required ? true : column.visible"
            :disabled="column.required"
            @change="(value: string | number | boolean) => emit('toggleColumn', column.key, value)"
          />
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.settings-section + .settings-section {
  margin-top: 20px;
}

.settings-section-compact {
  padding: 12px 12px 14px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: #fbfcff;
}

.settings-title {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
}

.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.settings-note {
  margin-top: 4px;
  color: var(--text-subtle);
  font-size: 12px;
  line-height: 1.4;
}

.page-size-segment {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  margin-top: 10px;
  padding: 4px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: #fff;
}

.page-size-option {
  min-width: 58px;
  height: 32px;
  padding: 0 12px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text-main);
  font-size: 13px;
  font-weight: 500;
  line-height: 32px;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.page-size-option:hover {
  background: rgba(36, 107, 255, 0.08);
}

.page-size-option-active {
  background: var(--el-color-primary);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(36, 107, 255, 0.2);
}

.settings-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}

.settings-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 38px;
  padding: 6px 10px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
}

.settings-item-dragging {
  opacity: 0.65;
  border-color: rgba(36, 107, 255, 0.45);
  background: rgba(36, 107, 255, 0.04);
}

.settings-item-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.settings-item-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 13px;
  line-height: 1.2;
}

.settings-drag-handle {
  cursor: grab;
  color: var(--text-subtle);
  font-size: 13px;
}

.settings-required {
  color: var(--text-subtle);
  font-size: 12px;
}
</style>
