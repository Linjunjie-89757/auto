<script setup lang="ts">
import { Home } from '@lucide/vue'
</script>

<template>
  <section class="dashboard-placeholder-shell">
    <div class="dashboard-placeholder">
      <div>
        <Home :size="34" />
        <p>工作台 页面建设中...</p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.dashboard-placeholder-shell {
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: #f9fafb;
}

.dashboard-placeholder {
  display: flex;
  min-height: 100%;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 14px;
  text-align: center;
}

.dashboard-placeholder div {
  display: grid;
  justify-items: center;
  gap: 12px;
}

.dashboard-placeholder p {
  margin: 0;
}
</style>
