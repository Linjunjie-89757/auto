import type {
  ApiResponse,
  AiCaseConfig,
  AiCaseConfigResponse,
  AiCaseConfigSecretResponse,
  AiGenerateResponse,
  AiRequirementAsset,
  ImportRequirementDocumentResponse,
  AiReviewResult,
  BugDetail,
  BugStats,
  BugSummary,
  CaseDirectoryNode,
  CaseDirectoryWorkspace,
  CaseDetail,
  CaseItem,
  CreateCaseDirectoryPayload,
  CreateCasePayload,
  CreateEnvPayload,
  CreateUserPayload,
  CreateBugPayload,
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
  DashboardSummary,
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
  TaskDetail,
  TaskItem,
  TaskTransitionPayload,
  TestAiCaseConfigResponse,
  MoveCaseDirectoryPayload,
  RenameCaseDirectoryPayload,
  UpdateReportContentPayload,
  UpdateUserPayload,
  ResetPasswordResponse,
  UserItem,
  WorkspaceMemberItem,
  WorkspaceItem,
} from '../types/api'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://127.0.0.1:8080/api'

export function resolveApiUrl(path: string) {
  if (!path) {
    return API_BASE
  }
  if (/^https?:\/\//i.test(path)) {
    return path
  }
  return `${API_BASE}${path.startsWith('/') ? path : `/${path}`}`
}

type RequestOptions = RequestInit & {
  workspaceCode?: string
}

function buildError(message: string, status: number) {
  const error = new Error(message) as Error & { status?: number }
  error.status = status
  return error
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { workspaceCode = 'account-open', headers, ...rest } = options
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

  const payload = await response.json() as ApiResponse<T>
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
  getBugStats(workspaceCode: string) {
    return request<BugStats>('/bugs/statistics', { workspaceCode })
  },
  getBugs(workspaceCode: string) {
    return request<PageResponse<BugSummary>>('/bugs', { workspaceCode })
  },
  getBugDetail(workspaceCode: string, id: number) {
    return request<BugDetail>(`/bugs/${id}`, { workspaceCode })
  },
  createBug(workspaceCode: string, payload: CreateBugPayload) {
    return request<BugDetail>('/bugs', {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  updateBug(workspaceCode: string, id: number, payload: CreateBugPayload) {
    return request<BugDetail>(`/bugs/${id}`, {
      method: 'PUT',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  assignBug(workspaceCode: string, id: number, assigneeId: number) {
    return request<BugDetail>(`/bugs/${id}/assign`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify({ assigneeId }),
    })
  },
  transitionBug(workspaceCode: string, id: number, toStatus: string, actionComment: string) {
    return request<BugDetail>(`/bugs/${id}/transition`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify({ toStatus, actionComment }),
    })
  },
  addBugComment(workspaceCode: string, id: number, content: string) {
    return request<void>(`/bugs/${id}/comments`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify({ content }),
    })
  },
  createBugFromCase(workspaceCode: string, caseId: number, payload: CreateBugPayload) {
    return request<BugDetail>(`/cases/${caseId}/bugs`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
  createBugFromReport(workspaceCode: string, reportId: number, payload: CreateBugPayload) {
    return request<BugDetail>(`/reports/${reportId}/bugs`, {
      method: 'POST',
      workspaceCode,
      body: JSON.stringify(payload),
    })
  },
}
