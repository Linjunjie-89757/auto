import { platformApi } from '../api/platform'
import type {
  AiGenerateResponse,
  AiGeneratedCase,
  AiGenerationOutputMode,
  AiGenerationTask,
  AiGenerationTaskStatus,
  AiInvalidCaseItem,
  AiReviewResult,
  CreateAiGenerationTaskPayload,
  UpdateAiGenerationTaskPayload,
} from '../types/api'

export type { AiGenerationOutputMode, AiGenerationTaskStatus }

export interface AiGenerationTaskRecord {
  id: string
  workspaceCode: string
  workspaceName: string
  createdByName: string
  updatedByName: string
  requirementTitle: string
  requirementContent: string
  requirementSummary: string
  outputMode: AiGenerationOutputMode
  status: AiGenerationTaskStatus
  currentStep: 1 | 2 | 3 | 4
  stepMessage: string
  provider: string
  model: string
  directoryId: number | null
  directoryName: string | null
  createdAt: string
  updatedAt: string
  finishedAt: string | null
  errorMessage: string | null
  generatedCount: number
  savedCaseCount: number
  warnings: string[]
  invalidCases: AiInvalidCaseItem[]
  generatedCases: AiGeneratedCase[]
  reviewResult: AiReviewResult | null
  adoptedCaseIndexes: number[]
  deletedCaseIndexes: number[]
  cancelRequested: boolean
  sourceTaskId: string | null
}

export function summarizeRequirementText(text: string, max = 120) {
  const normalized = text.replace(/\s+/g, ' ').trim()
  if (!normalized) {
    return '-'
  }
  return normalized.length > max ? `${normalized.slice(0, max)}...` : normalized
}

export function fromAiGenerationTask(task: AiGenerationTask): AiGenerationTaskRecord {
  return {
    id: task.taskId,
    workspaceCode: task.workspaceCode,
    workspaceName: task.workspaceName,
    createdByName: task.createdByName || '-',
    updatedByName: task.updatedByName || '-',
    requirementTitle: task.requirementTitle,
    requirementContent: task.requirementContent,
    requirementSummary: summarizeRequirementText(task.requirementContent),
    outputMode: task.outputMode,
    status: task.status,
    currentStep: task.currentStep,
    stepMessage: task.stepMessage,
    provider: task.provider || '-',
    model: task.model || '-',
    directoryId: task.directoryId,
    directoryName: task.directoryName,
    createdAt: task.createdAt || '',
    updatedAt: task.updatedAt || '',
    finishedAt: task.finishedAt,
    errorMessage: task.errorMessage,
    generatedCount: task.generatedCount,
    savedCaseCount: task.savedCaseCount,
    warnings: task.warnings ?? [],
    invalidCases: task.invalidCases ?? [],
    generatedCases: task.generatedCases ?? [],
    reviewResult: task.reviewResult,
    adoptedCaseIndexes: task.adoptedCaseIndexes ?? [],
    deletedCaseIndexes: task.deletedCaseIndexes ?? [],
    cancelRequested: task.cancelRequested,
    sourceTaskId: task.sourceTaskId,
  }
}

export async function listAiGenerationRecords(workspaceCode: string) {
  const tasks = await platformApi.listAiGenerationTasks(workspaceCode)
  return tasks.map(fromAiGenerationTask)
}

export async function getAiGenerationRecord(workspaceCode: string, recordId: string) {
  try {
    const task = await platformApi.getAiGenerationTask(workspaceCode, recordId)
    return fromAiGenerationTask(task)
  } catch (error) {
    if ((error as Error & { status?: number }).status === 400) {
      return null
    }
    throw error
  }
}

export async function createAiGenerationRecord(workspaceCode: string, payload: CreateAiGenerationTaskPayload) {
  return fromAiGenerationTask(await platformApi.createAiGenerationTask(workspaceCode, payload))
}

export async function saveAiGenerationRecord(workspaceCode: string, record: AiGenerationTaskRecord) {
  const payload: UpdateAiGenerationTaskPayload = {
    directoryId: record.directoryId,
    directoryName: record.directoryName,
    generatedCases: record.generatedCases,
    adoptedCaseIndexes: record.adoptedCaseIndexes,
    deletedCaseIndexes: record.deletedCaseIndexes,
    savedCaseCount: record.savedCaseCount,
  }
  return fromAiGenerationTask(await platformApi.updateAiGenerationTask(workspaceCode, record.id, payload))
}

export async function patchAiGenerationRecord(
  workspaceCode: string,
  recordId: string,
  patch: UpdateAiGenerationTaskPayload,
) {
  return fromAiGenerationTask(await platformApi.updateAiGenerationTask(workspaceCode, recordId, patch))
}

export async function removeAiGenerationRecord(workspaceCode: string, recordId: string) {
  await platformApi.deleteAiGenerationTask(workspaceCode, recordId)
}

export async function cancelAiGenerationRecord(workspaceCode: string, recordId: string) {
  return fromAiGenerationTask(await platformApi.cancelAiGenerationTask(workspaceCode, recordId))
}

export async function retryAiGenerationRecord(workspaceCode: string, recordId: string) {
  return fromAiGenerationTask(await platformApi.retryAiGenerationTask(workspaceCode, recordId))
}

export function createRecordFromGenerationResponse(
  baseRecord: AiGenerationTaskRecord,
  response: AiGenerateResponse,
): AiGenerationTaskRecord {
  return {
    ...baseRecord,
    provider: response.provider,
    model: response.model,
    generatedCount: response.actualGeneratedCount,
    warnings: response.warnings,
    invalidCases: response.invalidCases,
    generatedCases: response.generatedCases,
  }
}

export function ensureAiGenerationDemoRecords() {
  return
}

export function clearAiGenerationRecords() {
  return
}
