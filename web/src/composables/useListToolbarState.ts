import { computed, toValue, type MaybeRefOrGetter } from 'vue'
import { usePersistedFilters } from './usePersistedFilters'
import { useTableSettings, type TableSettingsColumn } from './useTableSettings'

type UseListToolbarStateOptions<TFilters extends Record<string, unknown>> = {
  tableSettingsKey: string
  filterStorageKey: MaybeRefOrGetter<string>
  columns: MaybeRefOrGetter<TableSettingsColumn[]>
  filters: TFilters
  filterDefaults: TFilters
  isColumnAvailable?: (column: TableSettingsColumn) => boolean
  pageSizeEnabled?: boolean
  defaultPageSize?: number
  pageSizeOptions?: number[]
}

export function useListToolbarState<TFilters extends Record<string, unknown>>(options: UseListToolbarStateOptions<TFilters>) {
  const tableSettings = useTableSettings({
    storageKey: options.tableSettingsKey,
    columns: options.columns,
    isColumnAvailable: options.isColumnAvailable,
    pageSizeEnabled: options.pageSizeEnabled,
    defaultPageSize: options.defaultPageSize,
    pageSizeOptions: options.pageSizeOptions,
  })

  const filterMemory = usePersistedFilters({
    storageKey: options.filterStorageKey,
    filters: options.filters,
    defaults: options.filterDefaults,
  })

  const drawerColumns = computed(() => tableSettings.settingsColumns.value.map(column => ({
    key: column.key,
    label: column.label,
    required: column.required,
    visible: column.required ? true : tableSettings.visibleColumns.value.some(item => item.key === column.key),
    draggable: tableSettings.canDragColumn(column.key),
  })))

  function load() {
    tableSettings.load()
    filterMemory.load()
  }

  return {
    ...tableSettings,
    drawerColumns,
    filterMemory,
    load,
    columns: computed(() => toValue(options.columns)),
  }
}
