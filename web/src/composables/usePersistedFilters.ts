import { toValue, watch, type MaybeRefOrGetter } from 'vue'

type UsePersistedFiltersOptions<T extends Record<string, unknown>> = {
  storageKey: MaybeRefOrGetter<string>
  filters: T
  defaults: T
}

function cloneDefaults<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

export function usePersistedFilters<T extends Record<string, unknown>>(options: UsePersistedFiltersOptions<T>) {
  let ready = false

  function resolveStorageKey() {
    return toValue(options.storageKey)
  }

  function applyDefaults() {
    Object.assign(options.filters, cloneDefaults(options.defaults))
  }

  function load() {
    ready = false
    applyDefaults()
    const raw = localStorage.getItem(resolveStorageKey())
    if (!raw) {
      ready = true
      return
    }
    try {
      const parsed = JSON.parse(raw) as Partial<T>
      Object.assign(options.filters, cloneDefaults({
        ...options.defaults,
        ...parsed,
      }))
    }
    catch {
      applyDefaults()
    }
    finally {
      ready = true
    }
  }

  function persist() {
    localStorage.setItem(resolveStorageKey(), JSON.stringify(options.filters))
  }

  function reset() {
    applyDefaults()
    if (ready) {
      persist()
    }
  }

  watch(options.filters, () => {
    if (!ready) {
      return
    }
    persist()
  }, { deep: true })

  return {
    load,
    persist,
    reset,
  }
}
