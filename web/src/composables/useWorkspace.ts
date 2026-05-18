import { computed } from 'vue'
import { useRoute } from 'vue-router'

export function useWorkspace() {
  const route = useRoute()

  const workspaceCode = computed(() => route.query.workspace?.toString() ?? 'ALL')
  const isAllScope = computed(() => workspaceCode.value === 'ALL')

  return {
    workspaceCode,
    isAllScope,
  }
}
