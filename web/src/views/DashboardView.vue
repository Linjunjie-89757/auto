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
  color: var(--ath-text-subtle);
  font-size: var(--ath-font-sm);
  text-align: center;
}

.dashboard-placeholder div {
  display: grid;
  justify-items: center;
  gap: 12px;
  min-width: min(520px, calc(100vw - 64px));
  border: 1px solid var(--ath-border);
  border-radius: var(--ath-radius-lg);
  background: linear-gradient(180deg, #ffffff 0%, var(--ath-bg-subtle) 100%);
  padding: var(--ath-space-8);
  box-shadow: var(--ath-shadow-xs);
}

.dashboard-placeholder svg {
  width: 42px;
  height: 42px;
  border-radius: var(--ath-radius-lg);
  background: var(--ath-blue-soft);
  padding: 10px;
  color: var(--ath-primary);
}

.dashboard-placeholder p {
  margin: 0;
}
</style>
