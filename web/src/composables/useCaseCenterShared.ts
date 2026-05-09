import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { platformApi } from '../api/platform'
import { useWorkspace } from './useWorkspace'
import { useWorkspaceAccess } from './useWorkspaceAccess'
import type { UserItem, WorkspaceItem } from '../types/api'

export function useCaseCenterShared() {
  const { workspaceCode, isAllScope } = useWorkspace()
  const { canWriteWorkspace, isPlatformAdmin } = useWorkspaceAccess()

  const sharedLoading = ref(false)
  const users = ref<UserItem[]>([])
  const workspaces = ref<WorkspaceItem[]>([])

  const businessWorkspaces = computed(() => workspaces.value.filter(item => !item.allScope))
  const writableWorkspaces = computed(() => businessWorkspaces.value.filter(item => canWriteWorkspace(item.code)))
  const canCreateCase = computed(() => (
    isAllScope.value ? writableWorkspaces.value.length > 0 : canWriteWorkspace(workspaceCode.value)
  ))

  async function loadSharedBase() {
    sharedLoading.value = true
    try {
      const [userList, workspaceList] = await Promise.all([
        platformApi.getUsers(),
        platformApi.getSwitchableWorkspaces(),
      ])
      users.value = userList
      workspaces.value = workspaceList
    }
    catch (error) {
      ElMessage.error((error as Error).message)
    }
    finally {
      sharedLoading.value = false
    }
  }

  return {
    workspaceCode,
    isAllScope,
    isPlatformAdmin,
    canWriteWorkspace,
    sharedLoading,
    users,
    workspaces,
    businessWorkspaces,
    writableWorkspaces,
    canCreateCase,
    loadSharedBase,
  }
}
