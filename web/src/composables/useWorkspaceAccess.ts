import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'

export function useWorkspaceAccess() {
  const authStore = useAuthStore()

  const currentUser = computed(() => authStore.currentUser)
  const isSuperAdmin = computed(() => currentUser.value?.roleCode === 'SUPER_ADMIN')
  const isPlatformAdmin = computed(() => (
    isSuperAdmin.value
    || currentUser.value?.roleCode === 'ADMIN'
    || currentUser.value?.roleCode === 'PLATFORM_ADMIN'
  ))

  function canWriteWorkspace(workspaceCode: string) {
    return isPlatformAdmin.value || !!currentUser.value?.workspaceCodes.includes(workspaceCode)
  }

  return {
    currentUser,
    isSuperAdmin,
    isPlatformAdmin,
    canWriteWorkspace,
  }
}
