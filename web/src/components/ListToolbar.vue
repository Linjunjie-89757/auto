<script setup lang="ts">
import { Setting } from '@element-plus/icons-vue'

withDefaults(defineProps<{
  title: string
  subtitle?: string
  showSettings?: boolean
}>(), {
  subtitle: '',
  showSettings: false,
})

const emit = defineEmits<{
  settings: []
}>()
</script>

<template>
  <div class="list-toolbar">
    <div class="list-toolbar-main">
      <div class="list-toolbar-head">
        <div class="list-toolbar-title">{{ title }}</div>
        <slot name="title-extra" />
      </div>
      <div v-if="subtitle || $slots.filters" class="list-toolbar-subline">
        <div v-if="subtitle" class="list-toolbar-subtitle">{{ subtitle }}</div>
        <div v-if="$slots.filters" class="list-toolbar-filters">
          <slot name="filters" />
        </div>
      </div>
    </div>

    <div class="list-toolbar-actions">
      <el-button
        v-if="showSettings"
        text
        class="table-settings-trigger"
        @click="emit('settings')"
      >
        <el-icon><Setting /></el-icon>
      </el-button>
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.list-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.list-toolbar-main {
  min-width: 0;
  flex: 1;
}

.list-toolbar-head {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.list-toolbar-title {
  font-size: 17px;
  font-weight: 600;
  line-height: 1.4;
}

.list-toolbar-subline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 4px;
}

.list-toolbar-subtitle {
  color: var(--text-subtle);
  font-size: 13px;
  line-height: 1.5;
}

.list-toolbar-filters,
.list-toolbar-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.table-settings-trigger {
  width: 28px;
  height: 28px;
  padding: 0;
}

@media (max-width: 900px) {
  .list-toolbar,
  .list-toolbar-subline {
    flex-direction: column;
    align-items: stretch;
  }

  .list-toolbar-actions {
    justify-content: flex-start;
  }
}
</style>
