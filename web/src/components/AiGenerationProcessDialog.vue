<script setup lang="ts">
import { computed } from 'vue'
import { CircleClose } from '@element-plus/icons-vue'
import type { AiGenerationTaskRecord } from '../utils/caseAiGenerationRecords'

type ProcessStep = {
  index: AiGenerationTaskRecord['currentStep']
  title: string
  description: string
}

const props = withDefaults(defineProps<{
  modelValue: boolean
  record: AiGenerationTaskRecord | null
  steps: ProcessStep[]
  statusLabel: string
  statusClass: string
  showCancelButton?: boolean
  cancelDisabled?: boolean
}>(), {
  showCancelButton: false,
  cancelDisabled: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  cancel: []
}>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const isRunning = computed(() => (
  !!props.record && ['PENDING', 'GENERATING', 'REVIEWING'].includes(props.record.status)
))

function getFailureStepLabel(step: AiGenerationTaskRecord['currentStep']) {
  const labelMap: Record<AiGenerationTaskRecord['currentStep'], string> = {
    1: '任务创建',
    2: 'AI 生成用例',
    3: 'AI 自动评审',
    4: '任务完成',
  }
  return labelMap[step]
}

function getStepStatusLabel(step: AiGenerationTaskRecord['currentStep'], status: AiGenerationTaskRecord['status']) {
  if (status === 'FAILED') {
    return step === props.record?.currentStep ? '失败' : ''
  }
  if (status === 'COMPLETED') {
    return step === 4 ? '已完成' : ''
  }
  if (status === 'CANCELED') {
    return step === props.record?.currentStep ? '已终止' : ''
  }
  if (step !== props.record?.currentStep) {
    return ''
  }
  const labelMap: Partial<Record<AiGenerationTaskRecord['status'], string>> = {
    PENDING: '已创建',
    GENERATING: '生成中',
    REVIEWING: '评审中',
  }
  return labelMap[status] ?? ''
}
</script>

<template>
  <el-dialog v-model="dialogVisible" title="AI生成用例流程" width="760px" destroy-on-close>
    <template v-if="record">
      <div class="process-dialog-meta">
        <div>
          <div class="process-dialog-title">{{ record.requirementTitle }}</div>
          <div class="process-dialog-subtitle">
            {{ record.workspaceName }} / {{ record.outputMode === 'STREAM' ? '实时流式输出' : '完整输出' }}
          </div>
        </div>
      </div>

      <div class="process-step-list" :class="{ 'process-step-list-running': isRunning }">
        <div
          v-for="step in steps"
          :key="step.index"
          class="process-step-card"
          :class="{
            'process-step-card-active': record.currentStep === step.index,
            'process-step-card-done': record.currentStep > step.index || record.status === 'COMPLETED',
            'process-step-card-failed': record.status === 'FAILED' && record.currentStep === step.index,
            'process-step-card-running': isRunning && record.currentStep === step.index,
          }"
        >
          <div
            v-if="getStepStatusLabel(step.index, record.status)"
            class="process-step-status"
            :class="[
              statusClass,
              {
                'process-step-status-running': isRunning && record.currentStep === step.index,
              },
            ]"
          >
            <span
              v-if="isRunning && record.currentStep === step.index"
              class="process-step-status-spinner"
              aria-hidden="true"
            />
            <span>{{ getStepStatusLabel(step.index, record.status) }}</span>
          </div>
          <div
            class="process-step-index"
            :class="{
              'process-step-index-active': record.currentStep === step.index && record.status !== 'FAILED',
              'process-step-index-done': record.currentStep > step.index || record.status === 'COMPLETED',
              'process-step-index-failed': record.status === 'FAILED' && record.currentStep === step.index,
              'process-step-index-running': isRunning && record.currentStep === step.index,
            }"
          >
            {{ step.index }}
          </div>
          <div class="process-step-content">
            <div class="process-step-title">{{ step.title }}</div>
            <div class="process-step-desc">{{ step.description }}</div>
          </div>
        </div>
      </div>

      <div v-if="record.status === 'FAILED'" class="process-failure-card">
        <div class="process-current-label">失败详情</div>
        <div class="process-failure-stage">失败阶段：{{ getFailureStepLabel(record.currentStep) }}</div>
        <div class="process-failure-reason">失败原因：{{ record.errorMessage || record.stepMessage || '-' }}</div>
      </div>
    </template>

    <template #footer>
      <div class="dialog-footer">
        <el-button
          v-if="showCancelButton"
          type="danger"
          :icon="CircleClose"
          :disabled="cancelDisabled"
          @click="emit('cancel')"
        >
          取消生成
        </el-button>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.process-dialog-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

.process-dialog-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.process-dialog-subtitle,
.process-step-desc,
.process-current-text {
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-subtle);
}

.process-step-list {
  position: relative;
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.process-step-list-running::before {
  content: '';
  position: absolute;
  left: 17px;
  top: 18px;
  bottom: 18px;
  width: 2px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(191, 219, 254, 0.2) 0%, rgba(96, 165, 250, 0.6) 50%, rgba(191, 219, 254, 0.2) 100%);
  pointer-events: none;
}

.process-step-card {
  position: relative;
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  padding: 14px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.82);
}

.process-step-status {
  position: absolute;
  top: 12px;
  right: 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  z-index: 1;
}

.process-step-status-running {
  overflow: hidden;
}

.process-step-status-spinner {
  width: 12px;
  height: 12px;
  border: 1.5px solid currentColor;
  border-right-color: transparent;
  border-radius: 999px;
  animation: process-step-spinner 0.95s linear infinite;
  flex: 0 0 auto;
}

.process-step-card-active {
  border-color: rgba(36, 107, 255, 0.36);
  background: rgba(233, 240, 255, 0.82);
}

.process-step-card-running {
  animation: process-card-breathe 2.4s ease-in-out infinite;
  box-shadow: 0 0 0 0 rgba(47, 136, 255, 0.12);
}

.process-step-card-done {
  border-color: rgba(20, 163, 109, 0.22);
}

.process-step-card-failed {
  border-color: rgba(240, 68, 56, 0.26);
  background: rgba(254, 242, 242, 0.92);
}

.process-step-index {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.08);
  font-size: 14px;
  font-weight: 700;
  color: var(--text-main);
}

.process-step-index-active,
.process-step-index-done {
  background: #2f88ff;
  color: #ffffff;
}

.process-step-index-running {
  position: relative;
  animation: process-index-pulse 1.8s ease-in-out infinite;
}

.process-step-index-running::after {
  content: '';
  position: absolute;
  inset: -5px;
  border-radius: 999px;
  border: 1px solid rgba(47, 136, 255, 0.28);
  animation: process-index-ring 1.8s ease-out infinite;
}

.process-step-index-failed {
  background: #f04438;
  color: #ffffff;
}

.process-step-title,
.process-current-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
}

.process-failure-card {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid rgba(240, 68, 56, 0.18);
  border-radius: 10px;
  background: rgba(254, 242, 242, 0.96);
}

.process-failure-stage {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #7a271a;
}

.process-failure-reason {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: #b42318;
}

.status-pill {
  position: relative;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill-running::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-130%);
  background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.16) 45%, rgba(255, 255, 255, 0.38) 50%, rgba(255, 255, 255, 0.16) 55%, transparent 100%);
  animation: process-pill-sheen 2.4s ease-in-out infinite;
}

@keyframes process-step-spinner {
  to {
    transform: rotate(360deg);
  }
}

@keyframes process-card-breathe {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(47, 136, 255, 0.08);
    background: rgba(233, 240, 255, 0.82);
  }
  50% {
    box-shadow: 0 10px 24px -18px rgba(47, 136, 255, 0.5);
    background: rgba(226, 236, 255, 0.94);
  }
}

@keyframes process-index-pulse {
  0%,
  100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.06);
  }
}

@keyframes process-index-ring {
  0% {
    opacity: 0.48;
    transform: scale(0.94);
  }
  100% {
    opacity: 0;
    transform: scale(1.2);
  }
}

@keyframes process-pill-sheen {
  0%,
  20% {
    transform: translateX(-130%);
  }
  60%,
  100% {
    transform: translateX(130%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .process-step-card-running,
  .process-step-index-running,
  .process-step-index-running::after,
  .status-pill-running::after {
    animation: none !important;
  }
}
</style>
