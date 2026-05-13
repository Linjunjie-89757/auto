import type { CaseItem } from '../types/api'

const CASE_EXECUTION_CONTEXT_KEY = 'case-execution-context-v1'

export type CaseExecutionContext = {
  workspaceCode: string
  returnQuery: Record<string, string>
  selectedNodePath: string
  sourceLabel: string
  items: CaseItem[]
}

function isStringRecord(value: unknown): value is Record<string, string> {
  return !!value && typeof value === 'object' && Object.values(value).every(item => typeof item === 'string')
}

function isCaseItem(value: unknown): value is CaseItem {
  if (!value || typeof value !== 'object') {
    return false
  }
  const candidate = value as Partial<CaseItem>
  return typeof candidate.id === 'number'
    && typeof candidate.caseNo === 'string'
    && typeof candidate.title === 'string'
    && typeof candidate.workspaceCode === 'string'
    && typeof candidate.executionStatus === 'string'
}

export function saveCaseExecutionContext(context: CaseExecutionContext) {
  sessionStorage.setItem(CASE_EXECUTION_CONTEXT_KEY, JSON.stringify(context))
}

export function loadCaseExecutionContext(): CaseExecutionContext | null {
  const raw = sessionStorage.getItem(CASE_EXECUTION_CONTEXT_KEY)
  if (!raw) {
    return null
  }
  try {
    const parsed = JSON.parse(raw) as Partial<CaseExecutionContext>
    if (
      typeof parsed.workspaceCode !== 'string'
      || !isStringRecord(parsed.returnQuery)
      || typeof parsed.selectedNodePath !== 'string'
      || typeof parsed.sourceLabel !== 'string'
      || !Array.isArray(parsed.items)
      || !parsed.items.every(isCaseItem)
    ) {
      sessionStorage.removeItem(CASE_EXECUTION_CONTEXT_KEY)
      return null
    }
    return {
      workspaceCode: parsed.workspaceCode,
      returnQuery: parsed.returnQuery,
      selectedNodePath: parsed.selectedNodePath,
      sourceLabel: parsed.sourceLabel,
      items: parsed.items,
    }
  } catch {
    sessionStorage.removeItem(CASE_EXECUTION_CONTEXT_KEY)
    return null
  }
}
