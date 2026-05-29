import type {
  ApiDebugCasePayload,
  ApiDebugDefinitionPayload,
  ApiDefinitionCaseDetail,
  ApiDefinitionCaseChangeHistoryItem,
  ApiDefinitionCaseItem,
  ApiDefinitionCaseRunHistoryDetail,
  ApiDefinitionCaseRunHistoryItem,
  ApiResponse,
  ApiDefinitionDetail,
  ApiDefinitionItem,
  ApiEnvironmentItem,
  ApiRunPayload,
  ApiRunResponse,
  ApiRunStepResult,
  ApiScenarioDetail,
  ApiScenarioItem,
  ApiScenarioModuleItem,
  ApiVariableSetItem,
  AiCaseConfig,
  AiProviderConnection,
  AiProviderConnectionSecretResponse,
  AiProviderModel,
  AiCaseConfigResponse,
  AiCaseConfigSecretResponse,
  FetchAiProviderModelsResponse,
  PreviewAiProviderModelsPayload,
  PreviewAiProviderModelsResponse,
  AiGenerationTask,
  AiGenerateResponse,
  AiRequirementAsset,
  ImportRequirementDocumentResponse,
  AiReviewResult,
  BugAttachment,
  BugDetail,
  BugStats,
  BugSummary,
  CaseExecutionAttachment,
  CaseDirectoryNode,
  CaseDirectoryWorkspace,
  CaseDetail,
  CaseItem,
  CreateCaseDirectoryPayload,
  CreateCasePayload,
  CreateEnvPayload,
  CreateDbConnectionPayload,
  CreateUserPayload,
  CreateBugPayload,
  UpdateBugPayload,
  ExecuteCasePayload,
  CreateParamPayload,
  CreateReportPayload,
  CreateTaskPayload,
  BatchWorkspaceMemberPayload,
  BatchDeleteCasesPayload,
  BatchMoveCasesPayload,
  BatchUpdateCasesPayload,
  CreateWorkspaceMemberPayload,
  CreateWorkspacePayload,
  CurrentUser,
  CreateAiGenerationTaskPayload,
  DashboardSummary,
  DbConnectionItem,
  DbConnectionTestResult,
  EnvConfigItem,
  GenerateAiCasesPayload,
  PageResponse,
  ParamSetItem,
  ReportAttachment,
  ReportDetail,
  ReportItem,
  ReviewCasePayload,
  ReviewAiGeneratedCasesPayload,
  SaveAiCaseConfigPayload,
  SaveAiProviderConnectionPayload,
  TaskDetail,
  TaskItem,
  TaskTransitionPayload,
  TestAiCaseConfigResponse,
  TestAiProviderConnectionResponse,
  TestDbConnectionPayload,
  UpdateAiGenerationTaskPayload,
  MoveCaseDirectoryPayload,
  RenameCaseDirectoryPayload,
  UpdateReportContentPayload,
  UpdateUserPayload,
  ResetPasswordResponse,
  SaveApiDefinitionCasePayload,
  SaveApiDefinitionPayload,
  SaveApiEnvironmentPayload,
  SaveApiScenarioPayload,
  UserItem,
  SaveApiVariableSetPayload,
  WorkspaceMemberItem,
  WorkspaceItem,
} from '../types/api'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api'

export function resolveApiUrl(path: string) {
  if (!path) {
    return API_BASE
  }
  if (/^https?:\/\//i.test(path)) {
    return path
  }
  if (path.startsWith('/api/')) {
    return path
  }
  return `${API_BASE}${path.startsWith('/') ? path : `/${path}`}`
}

type RequestOptions = RequestInit & {
  workspaceCode?: string
}

function normalizeBugSummary<T extends Partial<BugSummary>>(bug: T): T & Pick<BugSummary, 'tags'> {
  return {
    ...bug,
    tags: Array.isArray(bug.tags) ? bug.tags : [],
  }
}

function normalizeBugDetail(detail: BugDetail): BugDetail {
  return {
    ...normalizeBugSummary(detail),
    attachments: Array.isArray(detail.attachments) ? detail.attachments : [],
    activities: Array.isArray(detail.activities) ? detail.activities : [],
    comments: Array.isArray(detail.comments) ? detail.comments : [],
    flows: Array.isArray(detail.flows) ? detail.flows : [],
    sourceContext: detail.sourceContext ?? {
      sourceType: detail.sourceType,
      caseSummary: null,
      reportSummary: null,
      taskSummary: null,
    },
  }
}

function buildError(message: string, status: number) {
  const error = new Error(message) as Error & { status?: number }
  error.status = status
  return error
}

function buildHttpErrorMessage(response: Response, fallback: string) {
  if (!response.status) {
    return fallback
  }
  const statusText = response.statusText ? ` ${response.statusText}` : ''
  return `${fallback} (HTTP ${response.status}${statusText})`
}

async function readApiResponse<T>(response: Response): Promise<ApiResponse<T>> {
  const body = await response.text()
  if (!body.trim()) {
    throw buildError(
      buildHttpErrorMessage(
        response,
        response.ok ? '服务返回空响应，请稍后重试' : '服务返回空错误响应，请检查后端服务是否正常启动',
      ),
      response.status,
    )
  }

  try {
    return JSON.parse(body) as ApiResponse<T>
  }
  catch {
    throw buildError(
      buildHttpErrorMessage(
        response,
        response.ok ? '服务返回了无法解析的响应，请稍后重试' : '服务返回了无法解析的错误响应',
      ),
      response.status,
    )
  }
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { workspaceCode = 'ALL', headers, ...rest } = options
  const mergedHeaders = new Headers(headers)
  if (!mergedHeaders.has('Content-Type') && rest.body && !(rest.body instanceof FormData)) {
    mergedHeaders.set('Content-Type', 'application/json')
  }
  if (workspaceCode) {
    mergedHeaders.set('X-Workspace-Code', workspaceCode)
  }

  const response = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    ...rest,
    headers: mergedHeaders,
  })

  const payload = await readApiResponse<T>(response)
  if (!response.ok || !payload.success) {
    throw buildError(payload.message || '请求失败', response.status)
  }
  return payload.data
}

export const platformApi = {
  login(username: string, password: string) {
    return request<CurrentUser>('/auth/login', {
      method: 'POST',
      workspaceCode: 'ALL',
      body: JSON.stringify({ username, password }),
    })
  },
  getCurrentUser() {
    return request<CurrentUser>('/auth/me', { workspaceCode: 'ALL' })
  },
  logout() {
    return request<void>('/auth/logout', {
      method: 'POST',
      workspaceCode: 'ALL',
    })
  },
  getSwitchableWorkspaces() {
    return request<WorkspaceItem[]>('/workspaces/switchable', { workspaceCode: 'ALL' })
  },
  getWorkspaces() {
    return request<WorkspaceItem[]>('/workspaces', { workspaceCode: 'ALL' })
  },
  createWorkspace(payload: CreateWorkspacePayload) {
    return request<WorkspaceItem>('/workspaces', {
      method: 'POST',
      workspaceCode: 'ALL',
      body: JSON.stringify(payload),
    })
  },
  updateWorkspace(workspaceCode: string, payload: CreateWorkspacePayload) {
    return request<WorkspaceItem>(`/workspaces/${workspaceCode}`, {
      method: 'PUT',
      workspaceCode: 'ALL',
      body: JSON.stringify(payload),
    })
  },
  deleteWorkspace(workspaceCode: string) {
    return request<void>(`/workspaces/${workspaceCode}`, {
      method: 'DELETE',
      workspaceCode: 'ALL',
    })
  },
  getWorkspaceMembers(workspaceCode: string) {
    return request<WorkspaceMemberItem[]>(`/workspaces/${workspaceCode}/members`, { workspaceCode: 'ALL' })
  },
  createWorkspaceMember(workspaceCode: string, payload: CreateWorkspaceMemberPayload) {
    return request<WorkspaceMemberItem>(`/workspaces/${workspaceCode}/members`, {
      method: 'POST',
      workspaceCode: 'ALL',
      body: JSON.stringify(payload),
    })
  },
  createWorkspaceMembers(workspaceCode: string, payload: BatchWorkspaceMemberPayload) {
    return request<WorkspaceMemberItem[]>(`/workspaces/${workspaceCode}/members/batch`, {
      method: 'POST',
      workspaceCode: 'ALL',
      body: JSON.stringify(payload),
    })
  },
  deleteWorkspaceMember(workspaceCode: string, memberId: number) {
    return request<void>(`/workspaces/${workspaceCode}/members/${memberId}`, {
      method: 'DELETE',
      workspaceCode: 'ALL',
    })
  },
  getDashboardSummary(workspaceCode: string) {
    return request<DashboardSummary>('/dashboard/summary', { workspaceCode })
  },
  getCases(workspaceCode: string, params?: { pageNo?: number, pageSize?: number, directoryId?: number | null }) {
    const search = new URLSearchParams()
    if (params?.pageNo) {
      search.set('pageNo', String(params.pageNo))
    }
    if (params?.pageSize) {
      search.set('pageSize', String(params.pageSize))
    }
    if (params?.directoryId !== undefined && params.directoryId !== null) {
      search.set('directoryId', String(params.directoryId))
    }
    const query = search.size ? `?${search.toString()}` : ''
    return request<PageResponse<CaseItem>>(`/cases${query}`, { workspaceCode })
  },
  getCaseDirectories(workspaceCode: string) {
    return request<CaseDirectoryWorkspace[]>('/cases/directories', { workspaceCode })
  },
  getAiCaseConfig(workspaceCode: string, targetWorkspaceCode?: string) {
    const query = targetWorkspaceCode ? `?targetWorkspaceCode=${encodeURIComponent(targetWorkspaceCode)}` : ''
    return request<AiCaseConfigResponse>(`/cases/ai/config${query}`, { workspaceCode })
  },
  getAiProviderConnections(workspaceCode: string) {
    return request<AiProviderConnection[]>('/cases/ai/providers', { workspaceCode })
  },
  createAiProviderConnection(workspaceCode: string, payload: SaveAiProviderConnectionPayload) {
    return request<AiProviderConnection>('/cases/ai/providers', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  previewAiProviderModels(workspaceCode: string, payload: PreviewAiProviderModelsPayload) {
    return request<PreviewAiProviderModelsResponse>('/cases/ai/providers/preview-models', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  updateAiProviderConnection(workspaceCode: string, id: number, payload: SaveAiProviderConnectionPayload) {
    return request<AiProviderConnection>(`/cases/ai/providers/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  deleteAiProviderConnection(workspaceCode: string, id: number) {
    return request<void>(`/cases/ai/providers/${id}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  testAiProviderConnection(workspaceCode: string, id: number) {
    return request<TestAiProviderConnectionResponse>(`/cases/ai/providers/${id}/test`, {
      method: 'POST',
      workspaceCode,
    })
  },
  fetchAiProviderModels(workspaceCode: string, id: number) {
    return request<FetchAiProviderModelsResponse>(`/cases/ai/providers/${id}/fetch-models`, {
      method: 'POST',
      workspaceCode,
    })
  },
  getAiProviderModels(workspaceCode: string, id: number) {
    return request<AiProviderModel[]>(`/cases/ai/providers/${id}/models`, { workspaceCode })
  },
  getAiProviderConnectionSecret(workspaceCode: string, id: number) {
    return request<AiProviderConnectionSecretResponse>(`/cases/ai/providers/${id}/secret`, { workspaceCode })
  },
  probeAiProviderModel(workspaceCode: string, id: number, modelName: string) {
    return request<AiProviderModel>(`/cases/ai/providers/${id}/models/probe`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify({ modelName }),
    })
  },
  getAiCaseConfigSecret(workspaceCode: string, id: number) {
    return request<AiCaseConfigSecretResponse>(`/cases/ai/config/${id}/secret`, { workspaceCode })
  },
  createAiCaseConfig(workspaceCode: string, payload: SaveAiCaseConfigPayload) {
    return request<AiCaseConfig>('/cases/ai/config', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  updateAiCaseConfig(workspaceCode: string, id: number, payload: SaveAiCaseConfigPayload) {
    return request<AiCaseConfig>(`/cases/ai/config/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  bootstrapAiCaseConfigFromLegacy(workspaceCode: string) {
    return request<AiCaseConfigResponse>('/cases/ai/config/bootstrap-from-legacy', {
      method: 'POST',
      workspaceCode,
    })
  },
  generateAiCases(workspaceCode: string, payload: GenerateAiCasesPayload) {
    return request<AiGenerateResponse>('/cases/ai/generate', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  importRequirementDocument(workspaceCode: string, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request<ImportRequirementDocumentResponse>('/cases/ai/requirement-import', {
      method: 'POST',
      workspaceCode,
      body: formData,
    })
  },
  uploadRequirementAssets(workspaceCode: string, files: File[]) {
    const formData = new FormData()
    files.forEach(file => formData.append('files', file))
    return request<AiRequirementAsset[]>('/cases/ai/assets', {
      method: 'POST',
      workspaceCode,
      body: formData,
    })
  },
  deleteRequirementAsset(workspaceCode: string, id: number) {
    return request<void>(`/cases/ai/assets/${id}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  testAiCaseConfig(workspaceCode: string, payload: SaveAiCaseConfigPayload) {
    return request<TestAiCaseConfigResponse>('/cases/ai/config/test', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  reviewAiGeneratedCases(workspaceCode: string, payload: ReviewAiGeneratedCasesPayload) {
    return request<AiReviewResult>('/cases/ai/review', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  createAiGenerationTask(workspaceCode: string, payload: CreateAiGenerationTaskPayload) {
    return request<AiGenerationTask>('/cases/ai/tasks', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  listAiGenerationTasks(workspaceCode: string) {
    return request<AiGenerationTask[]>('/cases/ai/tasks', { workspaceCode })
  },
  getAiGenerationTask(workspaceCode: string, taskId: string) {
    return request<AiGenerationTask>(`/cases/ai/tasks/${encodeURIComponent(taskId)}`, { workspaceCode })
  },
  updateAiGenerationTask(workspaceCode: string, taskId: string, payload: UpdateAiGenerationTaskPayload) {
    return request<AiGenerationTask>(`/cases/ai/tasks/${encodeURIComponent(taskId)}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  deleteAiGenerationTask(workspaceCode: string, taskId: string) {
    return request<void>(`/cases/ai/tasks/${encodeURIComponent(taskId)}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  cancelAiGenerationTask(workspaceCode: string, taskId: string) {
    return request<AiGenerationTask>(`/cases/ai/tasks/${encodeURIComponent(taskId)}/cancel`, {
      method: 'POST',
      workspaceCode,
    })
  },
  retryAiGenerationTask(workspaceCode: string, taskId: string) {
    return request<AiGenerationTask>(`/cases/ai/tasks/${encodeURIComponent(taskId)}/retry`, {
      method: 'POST',
      workspaceCode,
    })
  },
  getCaseDetail(workspaceCode: string, id: number) {
    return request<CaseDetail>(`/cases/${id}`, { workspaceCode })
  },
  createCase(workspaceCode: string, payload: CreateCasePayload) {
    return request<CaseItem>('/cases', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  createCaseDirectory(workspaceCode: string, payload: CreateCaseDirectoryPayload) {
    return request<CaseDirectoryNode>('/cases/directories', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  updateCase(workspaceCode: string, id: number, payload: CreateCasePayload) {
    return request<CaseItem>(`/cases/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  reviewCase(workspaceCode: string, id: number, payload: ReviewCasePayload) {
    return request<CaseDetail>(`/cases/${id}/review`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  aiReviewCase(workspaceCode: string, id: number) {
    return request<AiReviewResult>(`/cases/${id}/ai-review`, {
      method: 'POST',
      workspaceCode,
    })
  },
  executeCase(workspaceCode: string, id: number, payload: ExecuteCasePayload) {
    return request<CaseDetail>(`/cases/${id}/execute`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  uploadCaseExecutionAttachment(workspaceCode: string, id: number, files: File[]) {
    const formData = new FormData()
    files.forEach(file => formData.append('files', file))
    return request<CaseExecutionAttachment[]>(`/cases/${id}/attachments`, {
      method: 'POST',
      workspaceCode,
      body: formData,
    })
  },
  deleteCaseExecutionAttachment(workspaceCode: string, caseId: number, attachmentId: number) {
    return request<void>(`/cases/${caseId}/attachments/${attachmentId}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  async downloadCaseExecutionAttachment(workspaceCode: string, caseId: number, attachmentId: number, fileName: string) {
    const response = await fetch(`${API_BASE}/cases/${caseId}/attachments/${attachmentId}/download`, {
      method: 'GET',
      credentials: 'include',
      headers: {
        'X-Workspace-Code': workspaceCode,
      },
    })
    if (!response.ok) {
      let message = '执行附件下载失败'
      try {
        const payload = await response.json() as ApiResponse<null>
        message = payload.message || message
      }
      catch {
        // ignore json parse failure for binary response
      }
      throw buildError(message, response.status)
    }
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  },
  batchMoveCases(workspaceCode: string, payload: BatchMoveCasesPayload) {
    return request<PageResponse<CaseItem>>('/cases/batch/move', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  batchUpdateCases(workspaceCode: string, payload: BatchUpdateCasesPayload) {
    return request<PageResponse<CaseItem>>('/cases/batch/update', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  batchDeleteCases(workspaceCode: string, payload: BatchDeleteCasesPayload) {
    return request<void>('/cases/batch/delete', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  renameCaseDirectory(workspaceCode: string, id: number, payload: RenameCaseDirectoryPayload) {
    return request<CaseDirectoryNode>(`/cases/directories/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  moveCaseDirectory(workspaceCode: string, id: number, payload: MoveCaseDirectoryPayload) {
    return request<CaseDirectoryNode>(`/cases/directories/${id}/move`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  deleteCase(workspaceCode: string, id: number) {
    return request<void>(`/cases/${id}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  deleteCaseDirectory(workspaceCode: string, id: number) {
    return request<void>(`/cases/directories/${id}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  getTasks(workspaceCode: string) {
    return request<PageResponse<TaskItem>>('/tasks', { workspaceCode })
  },
  getTaskDetail(workspaceCode: string, id: number) {
    return request<TaskDetail>(`/tasks/${id}`, { workspaceCode })
  },
  createTask(workspaceCode: string, payload: CreateTaskPayload) {
    return request<TaskItem>('/tasks', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  updateTask(workspaceCode: string, id: number, payload: CreateTaskPayload) {
    return request<TaskItem>(`/tasks/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  deleteTask(workspaceCode: string, id: number) {
    return request<void>(`/tasks/${id}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  transitionTask(workspaceCode: string, id: number, payload: TaskTransitionPayload) {
    return request<TaskDetail>(`/tasks/${id}/transition`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  getReports(workspaceCode: string) {
    return request<PageResponse<ReportItem>>('/reports', { workspaceCode })
  },
  getReportDetail(workspaceCode: string, id: number) {
    return request<ReportDetail>(`/reports/${id}`, { workspaceCode })
  },
  updateReportContent(workspaceCode: string, id: number, payload: UpdateReportContentPayload) {
    return request<ReportDetail>(`/reports/${id}/content`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  createReport(workspaceCode: string, payload: CreateReportPayload) {
    return request<ReportItem>('/reports', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  updateReport(workspaceCode: string, id: number, payload: CreateReportPayload) {
    return request<ReportItem>(`/reports/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  deleteReport(workspaceCode: string, id: number) {
    return request<void>(`/reports/${id}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  async uploadReportAttachment(workspaceCode: string, id: number, files: File[]) {
    const formData = new FormData()
    files.forEach(file => formData.append('files', file))
    return request<ReportAttachment[]>(`/reports/${id}/attachments`, {
      method: 'POST',
      workspaceCode,
      body: formData,
    })
  },
  deleteReportAttachment(workspaceCode: string, reportId: number, attachmentId: number) {
    return request<void>(`/reports/${reportId}/attachments/${attachmentId}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  getApiDefinitions(workspaceCode: string) {
    return request<PageResponse<ApiDefinitionItem>>('/automation/api/definitions', { workspaceCode })
  },
  getApiDefinitionDetail(workspaceCode: string, id: number) {
    return request<ApiDefinitionDetail>(`/automation/api/definitions/${id}`, { workspaceCode })
  },
  createApiDefinition(workspaceCode: string, payload: SaveApiDefinitionPayload) {
    return request<ApiDefinitionDetail>('/automation/api/definitions', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  updateApiDefinition(workspaceCode: string, id: number, payload: SaveApiDefinitionPayload) {
    return request<ApiDefinitionDetail>(`/automation/api/definitions/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  deleteApiDefinition(workspaceCode: string, id: number) {
    return request<void>(`/automation/api/definitions/${id}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  debugApiDefinition(workspaceCode: string, id: number, payload: ApiRunPayload) {
    return request<ApiRunResponse>(`/automation/api/definitions/${id}/debug-run`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  debugApiDefinitionDraft(workspaceCode: string, payload: ApiDebugDefinitionPayload) {
    return request<ApiRunResponse>('/automation/api/definitions/debug-run', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  getApiDefinitionCases(workspaceCode: string, params?: { definitionId?: number, keyword?: string }) {
    const query = new URLSearchParams()
    if (params?.definitionId != null) {
      query.set('definitionId', String(params.definitionId))
    }
    if (params?.keyword) {
      query.set('keyword', params.keyword)
    }
    const suffix = query.toString() ? `?${query.toString()}` : ''
    return request<PageResponse<ApiDefinitionCaseItem>>(`/automation/api/cases${suffix}`, { workspaceCode })
  },
  getApiDefinitionCaseDetail(workspaceCode: string, id: number) {
    return request<ApiDefinitionCaseDetail>(`/automation/api/cases/${id}`, { workspaceCode })
  },
  getApiDefinitionCaseRunHistory(workspaceCode: string, id: number) {
    return request<PageResponse<ApiDefinitionCaseRunHistoryItem>>(`/automation/api/cases/${id}/run-history`, { workspaceCode })
  },
  getApiDefinitionCaseRunHistoryDetail(workspaceCode: string, historyId: number) {
    return request<ApiDefinitionCaseRunHistoryDetail>(`/automation/api/cases/run-history/${historyId}`, { workspaceCode })
  },
  getApiDefinitionCaseChangeHistory(workspaceCode: string, id: number) {
    return request<PageResponse<ApiDefinitionCaseChangeHistoryItem>>(`/automation/api/cases/${id}/change-history`, { workspaceCode })
  },
  createApiDefinitionCase(workspaceCode: string, payload: SaveApiDefinitionCasePayload) {
    return request<ApiDefinitionCaseDetail>('/automation/api/cases', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  updateApiDefinitionCase(workspaceCode: string, id: number, payload: SaveApiDefinitionCasePayload) {
    return request<ApiDefinitionCaseDetail>(`/automation/api/cases/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  deleteApiDefinitionCase(workspaceCode: string, id: number) {
    return request<void>(`/automation/api/cases/${id}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  runApiDefinitionCase(workspaceCode: string, id: number, payload: ApiRunPayload) {
    return request<ApiRunResponse>(`/automation/api/cases/${id}/run`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  debugApiDefinitionCaseDraft(workspaceCode: string, payload: ApiDebugCasePayload) {
    return request<ApiRunResponse>('/automation/api/cases/debug-run', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  getApiScenarios(workspaceCode: string, filters?: { moduleId?: number | null; keyword?: string; status?: string }) {
    const params = new URLSearchParams()
    if (filters?.moduleId) {
      params.set('moduleId', String(filters.moduleId))
    }
    if (filters?.keyword?.trim()) {
      params.set('keyword', filters.keyword.trim())
    }
    if (filters?.status?.trim()) {
      params.set('status', filters.status.trim())
    }
    const suffix = params.toString() ? `?${params.toString()}` : ''
    return request<PageResponse<ApiScenarioItem>>(`/automation/api/scenarios${suffix}`, { workspaceCode })
  },
  getApiScenarioModules(workspaceCode: string) {
    return request<ApiScenarioModuleItem[]>('/automation/api/scenario-modules', { workspaceCode })
  },
  createApiScenarioModule(workspaceCode: string, payload: { workspaceCode?: string; parentId?: number | null; name: string }) {
    return request<ApiScenarioModuleItem>('/automation/api/scenario-modules', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  updateApiScenarioModule(workspaceCode: string, id: number, payload: { workspaceCode?: string; parentId?: number | null; name: string }) {
    return request<ApiScenarioModuleItem>(`/automation/api/scenario-modules/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  deleteApiScenarioModule(workspaceCode: string, id: number) {
    return request<void>(`/automation/api/scenario-modules/${id}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  getApiScenarioDetail(workspaceCode: string, id: number) {
    return request<ApiScenarioDetail>(`/automation/api/scenarios/${id}`, { workspaceCode })
  },
  createApiScenario(workspaceCode: string, payload: SaveApiScenarioPayload) {
    return request<ApiScenarioDetail>('/automation/api/scenarios', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  updateApiScenario(workspaceCode: string, id: number, payload: SaveApiScenarioPayload) {
    return request<ApiScenarioDetail>(`/automation/api/scenarios/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  deleteApiScenario(workspaceCode: string, id: number) {
    return request<void>(`/automation/api/scenarios/${id}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  runApiScenario(workspaceCode: string, id: number, payload: ApiRunPayload) {
    return request<ApiRunResponse>(`/automation/api/scenarios/${id}/run`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  getApiEnvironments(workspaceCode: string) {
    return request<PageResponse<ApiEnvironmentItem>>('/automation/api/environments', { workspaceCode })
  },
  createApiEnvironment(workspaceCode: string, payload: SaveApiEnvironmentPayload) {
    return request<ApiEnvironmentItem>('/automation/api/environments', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  updateApiEnvironment(workspaceCode: string, id: number, payload: SaveApiEnvironmentPayload) {
    return request<ApiEnvironmentItem>(`/automation/api/environments/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  deleteApiEnvironment(workspaceCode: string, id: number) {
    return request<void>(`/automation/api/environments/${id}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  getApiVariableSets(workspaceCode: string) {
    return request<PageResponse<ApiVariableSetItem>>('/automation/api/variable-sets', { workspaceCode })
  },
  createApiVariableSet(workspaceCode: string, payload: SaveApiVariableSetPayload) {
    return request<ApiVariableSetItem>('/automation/api/variable-sets', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  updateApiVariableSet(workspaceCode: string, id: number, payload: SaveApiVariableSetPayload) {
    return request<ApiVariableSetItem>(`/automation/api/variable-sets/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  deleteApiVariableSet(workspaceCode: string, id: number) {
    return request<void>(`/automation/api/variable-sets/${id}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  getApiRunStepResults(workspaceCode: string, reportId: number) {
    return request<ApiRunStepResult[]>(`/automation/api/runs/reports/${reportId}/steps`, { workspaceCode })
  },
  async downloadReportAttachment(workspaceCode: string, reportId: number, attachmentId: number, fileName: string) {
    const response = await fetch(`${API_BASE}/reports/${reportId}/attachments/${attachmentId}/download`, {
      method: 'GET',
      credentials: 'include',
      headers: {
        'X-Workspace-Code': workspaceCode,
      },
    })
    if (!response.ok) {
      let message = '附件下载失败'
      try {
        const payload = await response.json() as ApiResponse<null>
        message = payload.message || message
      }
      catch {
        // ignore json parse failure for binary response
      }
      throw buildError(message, response.status)
    }
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  },
  async uploadBugAttachment(workspaceCode: string, id: number, files: File[]) {
    const formData = new FormData()
    files.forEach(file => formData.append('files', file))
    return request<BugAttachment[]>(`/bugs/${id}/attachments`, {
      method: 'POST',
      workspaceCode,
      body: formData,
    })
  },
  deleteBugAttachment(workspaceCode: string, bugId: number, attachmentId: number) {
    return request<void>(`/bugs/${bugId}/attachments/${attachmentId}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  async downloadBugAttachment(workspaceCode: string, bugId: number, attachmentId: number, fileName: string) {
    const response = await fetch(`${API_BASE}/bugs/${bugId}/attachments/${attachmentId}/download`, {
      method: 'GET',
      credentials: 'include',
      headers: {
        'X-Workspace-Code': workspaceCode,
      },
    })
    if (!response.ok) {
      let message = '附件下载失败'
      try {
        const payload = await response.json() as ApiResponse<null>
        message = payload.message || message
      }
      catch {
        // ignore json parse failure for binary response
      }
      throw buildError(message, response.status)
    }
    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  },
  getUsers() {
    return request<UserItem[]>('/users', { workspaceCode: 'ALL' })
  },
  createUser(payload: CreateUserPayload) {
    return request<UserItem>('/users', {
      method: 'POST',
      workspaceCode: 'ALL',
      body: JSON.stringify(payload),
    })
  },
  updateUser(userId: number, payload: UpdateUserPayload) {
    return request<UserItem>(`/users/${userId}`, {
      method: 'PUT',
      workspaceCode: 'ALL',
      body: JSON.stringify(payload),
    })
  },
  resetUserPassword(userId: number) {
    return request<ResetPasswordResponse>(`/users/${userId}/reset-password`, {
      method: 'POST',
      workspaceCode: 'ALL',
    })
  },
  getSettingsEnvs(workspaceCode: string) {
    return request<PageResponse<EnvConfigItem>>('/settings/envs', { workspaceCode })
  },
  createSettingsEnv(workspaceCode: string, payload: CreateEnvPayload) {
    return request<EnvConfigItem>('/settings/envs', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  updateSettingsEnv(workspaceCode: string, id: number, payload: CreateEnvPayload) {
    return request<EnvConfigItem>(`/settings/envs/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  deleteSettingsEnv(workspaceCode: string, id: number) {
    return request<void>(`/settings/envs/${id}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  updateSettingsEnvStatus(workspaceCode: string, id: number, status: number) {
    return request<EnvConfigItem>(`/settings/envs/${id}/status`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify({ status }),
    })
  },
  getSettingsParams(workspaceCode: string) {
    return request<PageResponse<ParamSetItem>>('/settings/params', { workspaceCode })
  },
  createSettingsParam(workspaceCode: string, payload: CreateParamPayload) {
    return request<ParamSetItem>('/settings/params', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  updateSettingsParam(workspaceCode: string, id: number, payload: CreateParamPayload) {
    return request<ParamSetItem>(`/settings/params/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  deleteSettingsParam(workspaceCode: string, id: number) {
    return request<void>(`/settings/params/${id}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  updateSettingsParamStatus(workspaceCode: string, id: number, status: number) {
    return request<ParamSetItem>(`/settings/params/${id}/status`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify({ status }),
    })
  },
  getSettingsDbConnections(workspaceCode: string) {
    return request<PageResponse<DbConnectionItem>>('/settings/db-connections', { workspaceCode })
  },
  createSettingsDbConnection(workspaceCode: string, payload: CreateDbConnectionPayload) {
    return request<DbConnectionItem>('/settings/db-connections', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  updateSettingsDbConnection(workspaceCode: string, id: number, payload: CreateDbConnectionPayload) {
    return request<DbConnectionItem>(`/settings/db-connections/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  deleteSettingsDbConnection(workspaceCode: string, id: number) {
    return request<void>(`/settings/db-connections/${id}`, {
      method: 'DELETE',
      workspaceCode,
    })
  },
  updateSettingsDbConnectionStatus(workspaceCode: string, id: number, status: number) {
    return request<DbConnectionItem>(`/settings/db-connections/${id}/status`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify({ status }),
    })
  },
  testSettingsDbConnection(workspaceCode: string, payload: TestDbConnectionPayload) {
    return request<DbConnectionTestResult>('/settings/db-connections/test', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  getBugStats(workspaceCode: string) {
    return request<BugStats>('/bugs/statistics', { workspaceCode })
  },
  async getBugs(workspaceCode: string) {
    const page = await request<PageResponse<BugSummary>>('/bugs', { workspaceCode })
    return {
      ...page,
      items: Array.isArray(page.items) ? page.items.map(item => normalizeBugSummary(item)) : [],
    }
  },
  async getBugDetail(workspaceCode: string, id: number) {
    const detail = await request<BugDetail>(`/bugs/${id}`, { workspaceCode })
    return normalizeBugDetail(detail)
  },
  async createBug(workspaceCode: string, payload: CreateBugPayload) {
    const detail = await request<BugDetail>('/bugs', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
    return normalizeBugDetail(detail)
  },
  async updateBug(workspaceCode: string, id: number, payload: UpdateBugPayload) {
    const detail = await request<BugDetail>(`/bugs/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
    return normalizeBugDetail(detail)
  },
  async assignBug(workspaceCode: string, id: number, assigneeId: number) {
    const detail = await request<BugDetail>(`/bugs/${id}/assign`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify({ assigneeId }),
    })
    return normalizeBugDetail(detail)
  },
  async transitionBug(workspaceCode: string, id: number, toStatus: string, actionComment: string) {
    const detail = await request<BugDetail>(`/bugs/${id}/transition`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify({ toStatus, actionComment }),
    })
    return normalizeBugDetail(detail)
  },
  addBugComment(workspaceCode: string, id: number, content: string) {
    return request<void>(`/bugs/${id}/comments`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify({ content }),
    })
  },
  async createBugFromCase(workspaceCode: string, caseId: number, payload: CreateBugPayload) {
    const detail = await request<BugDetail>(`/cases/${caseId}/bugs`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
    return normalizeBugDetail(detail)
  },
  async createBugFromReport(workspaceCode: string, reportId: number, payload: CreateBugPayload) {
    const detail = await request<BugDetail>(`/reports/${reportId}/bugs`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
    return normalizeBugDetail(detail)
  },
}
