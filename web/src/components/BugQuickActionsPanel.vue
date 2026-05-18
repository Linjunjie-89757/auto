<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { BugDetail } from '../types/api'
import { formatBugDateTime, formatBugSeverity, formatBugStatus } from '../utils/bugPresentation'

const props = withDefaults(defineProps<{
  detail: BugDetail
  canWrite?: boolean
  transitioning?: boolean
  commenting?: boolean
}>(), {
  canWrite: false,
  transitioning: false,
  commenting: false,
})

const emit = defineEmits<{
  (event: 'transition', payload: { status: string, comment: string }): void
  (event: 'comment', content: string): void
}>()

const transitionStatus = ref('')
const transitionComment = ref('')
const commentText = ref('')

const statusOptions = [
  { label: '待处理', value: 'TODO' },
  { label: '已指派', value: 'ASSIGNED' },
  { label: '处理中', value: 'IN_PROGRESS' },
  { label: '待验证', value: 'PENDING_VERIFY' },
  { label: '已关闭', value: 'CLOSED' },
  { label: '已拒绝', value: 'REJECTED' },
]

const selectableStatusOptions = computed(() => statusOptions.filter(item => item.value !== props.detail.status))
const latestFlow = computed(() => props.detail.flows[props.detail.flows.length - 1] || null)

watch(() => props.detail.id, () => {
  transitionStatus.value = ''
  transitionComment.value = ''
  commentText.value = ''
})

function submitTransition() {
  if (!transitionStatus.value) {
    return
  }
  emit('transition', {
    status: transitionStatus.value,
    comment: transitionComment.value.trim(),
  })
  transitionStatus.value = ''
  transitionComment.value = ''
}

function submitComment() {
  const content = commentText.value.trim()
  if (!content) {
    return
  }
  emit('comment', content)
  commentText.value = ''
}
</script>

<template>
  <aside class="bug-quick-actions">
    <section class="bug-quick-card">
      <div class="bug-quick-card-title">当前状态</div>
      <div class="bug-quick-card-value">{{ formatBugStatus(detail.status) }}</div>
      <div class="bug-quick-card-meta">最近处理人：{{ latestFlow?.operatorName || detail.updatedByName || '-' }}</div>
      <div class="bug-quick-card-meta">最近处理时间：{{ formatBugDateTime(latestFlow?.createdAt || detail.updatedAt) }}</div>
    </section>

    <section class="bug-quick-card">
      <div class="bug-quick-card-title">处理人信息</div>
      <div class="bug-quick-card-row">
        <span>处理人</span>
        <strong>{{ detail.assigneeName || '-' }}</strong>
      </div>
      <div class="bug-quick-card-row">
        <span>提交人</span>
        <strong>{{ detail.reporterName || '-' }}</strong>
      </div>
      <div class="bug-quick-card-row">
        <span>工作空间</span>
        <strong>{{ detail.workspaceName }}</strong>
      </div>
      <div class="bug-quick-card-row">
        <span>严重程度</span>
        <strong>{{ formatBugSeverity(detail.severity) }}</strong>
      </div>
    </section>

    <section class="bug-quick-card">
      <div class="bug-quick-card-title">快捷流转</div>
      <div v-if="canWrite" class="bug-quick-form">
        <el-select v-model="transitionStatus" placeholder="选择目标状态">
          <el-option v-for="item in selectableStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input v-model="transitionComment" type="textarea" :rows="3" placeholder="填写流转说明（选填）" />
        <el-button type="primary" :disabled="!transitionStatus" :loading="transitioning" @click="submitTransition">
          更新状态
        </el-button>
      </div>
      <div v-else class="bug-quick-card-meta">当前账号仅可查看，无法流转状态。</div>
    </section>

    <section class="bug-quick-card">
      <div class="bug-quick-card-title">快捷评论</div>
      <div v-if="canWrite" class="bug-quick-form">
        <el-input v-model="commentText" type="textarea" :rows="4" placeholder="输入评论内容" />
        <el-button type="primary" :disabled="!commentText.trim()" :loading="commenting" @click="submitComment">
          添加评论
        </el-button>
      </div>
      <div v-else class="bug-quick-card-meta">当前账号仅可查看，无法发表评论。</div>
    </section>
  </aside>
</template>

<style scoped>
.bug-quick-actions {
  display: grid;
  gap: 12px;
}

.bug-quick-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fff;
}

.bug-quick-card-title {
  font-size: 14px;
  font-weight: 700;
  line-height: 1.5;
  color: #344054;
}

.bug-quick-card-value {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  color: #344054;
}

.bug-quick-card-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  line-height: 1.6;
  color: #475467;
}

.bug-quick-card-row strong {
  min-width: 0;
  text-align: right;
  color: #344054;
}

.bug-quick-card-meta {
  font-size: 12px;
  line-height: 1.6;
  color: #667085;
}

.bug-quick-form {
  display: grid;
  gap: 12px;
}
</style>
