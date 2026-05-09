<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowRight, Histogram, MagicStick, Timer } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { platformApi } from '../api/platform'
import { useWorkspace } from '../composables/useWorkspace'
import { quickActions } from '../data/platform'
import type { DashboardSummary } from '../types/api'

const { workspaceCode, isAllScope } = useWorkspace()
const loading = ref(false)
const summary = ref<DashboardSummary | null>(null)

const engineRows = computed(() => summary.value?.engineOverviews ?? [])
const recentActivities = computed(() => summary.value?.recentActivities ?? [])
const dashboardMetrics = computed(() => summary.value?.metrics ?? [])

async function loadSummary() {
  loading.value = true
  try {
    summary.value = await platformApi.getDashboardSummary(workspaceCode.value)
  }
  catch (error) {
    ElMessage.error((error as Error).message)
  }
  finally {
    loading.value = false
  }
}

watch(workspaceCode, loadSummary)
onMounted(loadSummary)
</script>

<template>
  <section class="page-shell">
    <div class="page-header">
      <div>
        <div class="page-title">工作台</div>
        <div class="page-description">
          聚合用例生产、自动化执行、失败分布和近期动态，作为测试团队的统一入口。
        </div>
      </div>

      <div class="page-actions">
        <el-button type="primary">
          <el-icon><MagicStick /></el-icon>
          AI 生成用例
        </el-button>
        <el-button>
          <el-icon><ArrowRight /></el-icon>
          新建执行任务
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="stats-grid">
      <article
        v-for="item in dashboardMetrics"
        :key="item.label"
        class="metric-card"
      >
        <div class="metric-label">{{ item.label }}</div>
        <div class="metric-value">{{ item.value }}</div>
        <div class="metric-trend">{{ item.trend }}</div>
      </article>
    </div>

    <div class="panel-grid">
      <article class="panel-card">
        <div class="panel-header">
          <div>
            <div class="panel-title">AI 用例生成趋势</div>
            <div class="panel-subtitle">近 7 天需求解析和补充生成都集中在开户与交易链路。</div>
          </div>
          <el-icon><MagicStick /></el-icon>
        </div>
        <el-progress :percentage="78" :stroke-width="14" status="success" />
        <div class="block-note" style="margin-top: 16px;">
          当前最值得优先建设的是“AI 生成后评审和保存流程”，这是平台最容易快速见效的一段链路。
        </div>
      </article>

      <article class="panel-card">
        <div class="panel-header">
          <div>
            <div class="panel-title">自动化执行概况</div>
          <div class="panel-subtitle">
            {{ isAllScope ? '当前为全部空间视角，展示跨项目汇总执行概况。' : '当前为单空间视角，展示所属项目执行概况。' }}
          </div>
        </div>
        <el-icon><Histogram /></el-icon>
      </div>
      <div class="list-stack">
          <div v-for="item in engineRows" :key="item.engineType" class="list-row">
            <div class="list-main">
              <div class="list-title">{{ item.label }}</div>
              <div class="list-meta">{{ item.detail }}</div>
            </div>
            <span :class="`status-pill status-${item.tone}`">{{ item.passRate }}</span>
          </div>
        </div>
      </article>

      <article class="panel-card">
        <div class="panel-header">
          <div>
            <div class="panel-title">近期慢任务排行</div>
            <div class="panel-subtitle">优先盯回归场景和稳定性差的执行项。</div>
          </div>
          <el-icon><Timer /></el-icon>
        </div>
        <div class="list-stack">
          <div class="list-row">
            <div class="list-main">
              <div class="list-title">web-regression-payment</div>
              <div class="list-meta mono">18m 42s · Chrome Headless</div>
            </div>
            <span class="status-pill status-warning">Web</span>
          </div>
          <div class="list-row">
            <div class="list-main">
              <div class="list-title">app-login-and-transfer</div>
              <div class="list-meta mono">14m 26s · iPhone 14</div>
            </div>
            <span class="status-pill status-danger">APP</span>
          </div>
          <div class="list-row">
            <div class="list-main">
              <div class="list-title">api-trade-settlement</div>
              <div class="list-meta mono">8m 09s · UAT</div>
            </div>
            <span class="status-pill status-success">API</span>
          </div>
        </div>
      </article>
    </div>

    <div class="double-grid">
      <article class="panel-card">
        <div class="panel-header">
          <div>
            <div class="panel-title">快捷操作</div>
            <div class="panel-subtitle">把高频动作压缩到三步内完成。</div>
          </div>
        </div>
        <div class="quick-grid">
          <div
            v-for="item in quickActions"
            :key="item.title"
            class="quick-action"
          >
            <div class="quick-action-title">{{ item.title }}</div>
            <div class="quick-action-desc">{{ item.desc }}</div>
          </div>
        </div>
      </article>

      <article class="panel-card">
        <div class="panel-header">
          <div>
            <div class="panel-title">最近动态</div>
            <div class="panel-subtitle">让团队成员不用翻多个模块也能知道刚发生了什么。</div>
          </div>
        </div>
        <div class="list-stack">
          <div
            v-for="item in recentActivities"
            :key="item.title"
            class="list-row"
          >
            <div class="list-main">
              <div class="list-title">{{ item.title }}</div>
              <div class="list-meta">{{ item.meta }}</div>
            </div>
            <span :class="`status-pill status-${item.tone}`">{{ item.status }}</span>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
