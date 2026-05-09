import { computed, ref, toValue, type MaybeRefOrGetter } from 'vue'

export type TableSettingsColumn = {
  key: string
  label: string
  required?: boolean
  defaultVisible?: boolean
  allOnly?: boolean
}

type UseTableSettingsOptions = {
  storageKey: string
  columns: MaybeRefOrGetter<TableSettingsColumn[]>
  isColumnAvailable?: (column: TableSettingsColumn) => boolean
  pageSizeEnabled?: boolean
  defaultPageSize?: number
  pageSizeOptions?: number[]
}

type PersistedSettings = {
  pageSize?: number
  columns?: Record<string, boolean>
  columnOrder?: string[]
}

export function useTableSettings(options: UseTableSettingsOptions) {
  const pageSizeOptions = options.pageSizeOptions ?? [10, 20, 30, 40, 50]
  const defaultPageSize = options.defaultPageSize ?? 10
  const settingsVisible = ref(false)
  const pageSize = ref(defaultPageSize)
  const pageSizeDisplay = ref(defaultPageSize)
  const draggingColumnKey = ref<string | null>(null)
  const columnVisibility = ref<Record<string, boolean>>({})
  const columnOrder = ref<string[]>([])

  const allColumns = computed<TableSettingsColumn[]>(() => toValue(options.columns))
  const availableColumns = computed(() => allColumns.value.filter((column: TableSettingsColumn) => (
    options.isColumnAvailable ? options.isColumnAvailable(column) : true
  )))
  const settingsColumns = computed(() => columnOrder.value
    .map(key => allColumns.value.find((column: TableSettingsColumn) => column.key === key))
    .filter((column): column is TableSettingsColumn => !!column)
    .filter(column => availableColumns.value.some((item: TableSettingsColumn) => item.key === column.key)))
  const visibleColumns = computed(() => settingsColumns.value.filter(column => (
    column.required || columnVisibility.value[column.key]
  )))

  function ensureDefaults() {
    const keys = allColumns.value.map((column: TableSettingsColumn) => column.key)
    columnOrder.value = keys
    columnVisibility.value = allColumns.value.reduce<Record<string, boolean>>((result: Record<string, boolean>, column: TableSettingsColumn) => {
      result[column.key] = column.required ? true : (column.defaultVisible ?? false)
      return result
    }, {})
  }

  function load() {
    ensureDefaults()
    const raw = localStorage.getItem(options.storageKey)
    if (!raw) {
      pageSizeDisplay.value = pageSize.value
      return
    }
    try {
      const parsed = JSON.parse(raw) as PersistedSettings
      const parsedOrder = parsed.columnOrder ?? []
      const nextOrder = allColumns.value
        .map((column: TableSettingsColumn) => column.key)
        .filter((key: string) => parsedOrder.includes(key))
      for (const column of allColumns.value) {
        if (!nextOrder.includes(column.key)) {
          nextOrder.push(column.key)
        }
      }
      columnOrder.value = nextOrder
      columnVisibility.value = allColumns.value.reduce<Record<string, boolean>>((result: Record<string, boolean>, column: TableSettingsColumn) => {
        result[column.key] = column.required ? true : (parsed.columns?.[column.key] ?? column.defaultVisible ?? false)
        return result
      }, {})
      if (options.pageSizeEnabled) {
        const nextPageSize = parsed.pageSize && pageSizeOptions.includes(parsed.pageSize)
          ? parsed.pageSize
          : defaultPageSize
        pageSize.value = nextPageSize
        pageSizeDisplay.value = nextPageSize
      }
    }
    catch {
      ensureDefaults()
      pageSize.value = defaultPageSize
      pageSizeDisplay.value = defaultPageSize
    }
  }

  function persist() {
    localStorage.setItem(options.storageKey, JSON.stringify({
      pageSize: options.pageSizeEnabled ? pageSize.value : undefined,
      columns: columnVisibility.value,
      columnOrder: columnOrder.value,
    }))
  }

  function toggleColumnVisibility(key: string, value: boolean | string | number) {
    const column = allColumns.value.find((item: TableSettingsColumn) => item.key === key)
    if (!column || column.required) {
      return
    }
    columnVisibility.value = {
      ...columnVisibility.value,
      [key]: Boolean(value),
    }
    persist()
  }

  function updatePageSize(size: number) {
    if (!options.pageSizeEnabled) {
      return
    }
    pageSize.value = size
    pageSizeDisplay.value = size
    persist()
  }

  function reset() {
    ensureDefaults()
    pageSize.value = defaultPageSize
    pageSizeDisplay.value = defaultPageSize
    persist()
  }

  function canDragColumn(key: string) {
    const column = allColumns.value.find((item: TableSettingsColumn) => item.key === key)
    return !!column && !column.required
  }

  function handleDragStart(key: string) {
    if (!canDragColumn(key)) {
      return
    }
    draggingColumnKey.value = key
  }

  function handleDragEnd() {
    draggingColumnKey.value = null
  }

  function moveColumnToTarget(targetKey: string) {
    const sourceKey = draggingColumnKey.value
    if (!sourceKey || sourceKey === targetKey || !canDragColumn(sourceKey) || !canDragColumn(targetKey)) {
      return
    }
    const currentIndex = columnOrder.value.indexOf(sourceKey)
    const targetIndex = columnOrder.value.indexOf(targetKey)
    if (currentIndex < 0 || targetIndex < 0) {
      return
    }
    const nextOrder = [...columnOrder.value]
    const [current] = nextOrder.splice(currentIndex, 1)
    nextOrder.splice(targetIndex, 0, current)
    columnOrder.value = nextOrder
    draggingColumnKey.value = null
    persist()
  }

  return {
    settingsVisible,
    pageSize,
    pageSizeDisplay,
    pageSizeOptions,
    settingsColumns,
    visibleColumns,
    draggingColumnKey,
    load,
    persist,
    reset,
    updatePageSize,
    toggleColumnVisibility,
    canDragColumn,
    handleDragStart,
    handleDragEnd,
    moveColumnToTarget,
  }
}
