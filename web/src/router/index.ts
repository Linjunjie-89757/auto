import { createRouter, createWebHistory } from 'vue-router'
import pinia from '../stores/pinia'
import { useAuthStore } from '../stores/auth'
import DashboardView from '../views/DashboardView.vue'
import CaseCenterView from '../views/CaseCenterView.vue'
import CaseAiConfigView from '../views/CaseAiConfigView.vue'
import CaseAiGenerateView from '../views/CaseAiGenerateView.vue'
import CaseAiRecordDetailView from '../views/CaseAiRecordDetailView.vue'
import CaseAiRecordsView from '../views/CaseAiRecordsView.vue'
import CaseManagementView from '../views/CaseManagementView.vue'
import AutomationView from '../views/AutomationView.vue'
import BugManagementView from '../views/BugManagementView.vue'
import SystemSettingsView from '../views/SystemSettingsView.vue'
import LoginView from '../views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/dashboard',
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true },
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: DashboardView,
    },
    {
      path: '/cases',
      component: CaseCenterView,
      children: [
        {
          path: '',
          redirect: to => ({ path: '/cases/manage', query: to.query }),
        },
        {
          path: 'manage',
          name: 'cases-manage',
          component: CaseManagementView,
        },
        {
          path: 'ai-generate',
          name: 'cases-ai-generate',
          component: CaseAiGenerateView,
        },
        {
          path: 'ai-config',
          name: 'cases-ai-config',
          component: CaseAiConfigView,
        },
        {
          path: 'ai-records',
          name: 'cases-ai-records',
          component: CaseAiRecordsView,
        },
        {
          path: 'ai-records/:taskId',
          name: 'cases-ai-record-detail',
          component: CaseAiRecordDetailView,
        },
      ],
    },
    {
      path: '/bugs',
      name: 'bugs',
      component: BugManagementView,
    },
    {
      path: '/automation/api',
      name: 'automation-api',
      component: AutomationView,
      props: {
        engine: 'api',
      },
    },
    {
      path: '/automation/web',
      name: 'automation-web',
      component: AutomationView,
      props: {
        engine: 'web',
      },
    },
    {
      path: '/automation/app',
      name: 'automation-app',
      component: AutomationView,
      props: {
        engine: 'app',
      },
    },
    {
      path: '/settings',
      name: 'settings',
      component: SystemSettingsView,
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore(pinia)
  await authStore.bootstrap()

  if (to.meta.public) {
    if (authStore.isAuthenticated) {
      return { path: '/dashboard', query: to.query }
    }
    return true
  }

  if (!authStore.isAuthenticated) {
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }

  return true
})

export default router
