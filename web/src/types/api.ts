export interface WorkspaceItem {
  code: string
  name: string
  description: string
  allScope: boolean
}

export interface ApiResponse<T> {
  success: boolean
  data: T
  message: string
}

export interface PageResponse<T> {
  items: T[]
  total: number
  pageNo: number
  pageSize: number
  totalPages: number
}

export interface CurrentUser {
  id: number
  username: string
  displayName: string
  roleCode: string
  workspaceCodes: string[]
}

export interface MetricCard {
  label: string
  value: string
  trend: string
}

export interface EngineOverview {
  engineType: string
  label: string
  detail: string
  passRate: string
  tone: string
}

export interface RecentActivity {
  title: string
  meta: string
  status: string
  tone: string
}

export interface DashboardSummary {
  workspaceCode: string
  workspaceName: string
  metrics: MetricCard[]
  engineOverviews: EngineOverview[]
  recentActivities: RecentActivity[]
}

export interface CaseItem {
  id: number
  caseNo: string
  title: string
  caseType: string
  priority: string
  sourceType: string
  status: string
  executionStatus: string
  ownerName: string
  executorName: string
  executionComment: string | null
  executionNote: string | null
  executedAt: string | null
  workspaceCode: string
  workspaceName: string
  directoryId: number | null
  directoryName: string | null
  createdBy: number | null
  createdByName: string | null
  createdAt: string | null
  updatedBy: number | null
  updatedByName: string | null
  updatedAt: string | null
  reviewStatus: string
  reviewComment: string | null
  reviewedBy: number | null
  reviewedByName: string | null
  reviewedAt: string | null
}

export interface CaseDetail extends CaseItem {
  ownerId: number | null
  executorId: number | null
  precondition: string
  steps: string
  expectedResult: string
  attachments: CaseExecutionAttachment[]
}

export interface CaseExecutionAttachment {
  id: number
  fileName: string
  contentType: string | null
  fileSize: number | null
  downloadUrl: string | null
  createdAt: string | null
}

export interface AiReviewResult {
  result: 'APPROVE' | 'REJECT' | 'SUGGEST'
  summary: string
  issues: string[]
  suggestions: string[]
  rawContent: string
  structured: boolean
}

export interface CreateCasePayload {
  workspaceCode?: string
  directoryId?: number | null
  title: string
  caseType: string
  priority: string
  sourceType: string
  caseStatus: string
  ownerId: number | null
  precondition: string
  steps: string
  expectedResult: string
}

export interface ReviewCasePayload {
  reviewStatus: string
  reviewComment?: string
}

export interface ExecuteCasePayload {
  executionStatus: string
  executionComment?: string
  executionNote?: string
}

export interface AiCaseConfig {
  id: number
  workspaceCode: string
  workspaceName: string
  roleType: 'CASE_GENERATOR' | 'CASE_REVIEWER'
  provider: string
  model: string
  baseUrl: string
  apiKeyMasked: string
  apiKeyConfigured: boolean
  promptTemplate: string
  reviewChecklist: string | null
  temperature: number
  maxCases: number
  supportsImageInput: boolean
  status: number
}

export interface AiCaseConfigResponse {
  generatorConfig: AiCaseConfig | null
  reviewerConfig: AiCaseConfig | null
}

export interface AiCaseConfigSecretResponse {
  id: number
  roleType: 'CASE_GENERATOR' | 'CASE_REVIEWER'
  apiKey: string
}

export interface ImportRequirementDocumentResponse {
  fileName: string
  title: string
  content: string
  charCount: number
  assets: AiRequirementAsset[]
}

export interface AiRequirementAsset {
  id: number
  sourceType: string
  fileName: string
  contentType: string | null
  fileSize: number | null
  extractedText: string | null
  downloadUrl: string | null
  createdAt: string | null
}

export interface SaveAiCaseConfigPayload {
  workspaceCode?: string
  roleType: 'CASE_GENERATOR' | 'CASE_REVIEWER'
  provider: string
  model: string
  baseUrl: string
  apiKey?: string
  promptTemplate: string
  reviewChecklist?: string
  temperature: number
  maxCases: number
  supportsImageInput?: boolean
  status?: number
}

export interface GenerateAiCasesPayload {
  workspaceCode?: string
  requirementTitle: string
  requirementContent: string
  sceneFocus?: string
  improvementNotes?: string
  assetIds?: number[]
  existingCases?: AiExistingCaseItem[]
  ownerId?: number | null
  maxCases?: number
}

export interface AiExistingCaseItem {
  title: string
  caseType: string
  priority: string
  precondition: string
  steps: string
  expectedResult: string
}

export interface AiGeneratedCase {
  title: string
  caseType: string
  priority: string
  precondition: string
  steps: string
  expectedResult: string
  riskNotes: string | null
  warnings: string[]
  savedDirectoryName?: string | null
  manualEdited?: boolean
  manualEditedByName?: string | null
  manualEditedAt?: string | null
}

export interface AiInvalidCaseItem {
  index: number
  title: string
  reason: string
}

export interface AiGenerateResponse {
  workspaceCode: string
  workspaceName: string
  provider: string
  model: string
  systemMaxCases: number
  requestedMaxCases: number
  effectiveMaxCases: number
  actualGeneratedCount: number
  generatedCases: AiGeneratedCase[]
  warnings: string[]
  invalidCases: AiInvalidCaseItem[]
  rawContent: string
}

export interface ReviewAiGeneratedCasesPayload {
  requirementTitle: string
  requirementContent: string
  sceneFocus?: string
  generatedCases: AiExistingCaseItem[]
}

export interface TestAiCaseConfigResponse {
  success: boolean
  provider: string
  model: string
  message: string
}

export type AiGenerationOutputMode = 'STREAM' | 'COMPLETE'
export type AiGenerationTaskStatus = 'PENDING' | 'GENERATING' | 'REVIEWING' | 'COMPLETED' | 'FAILED' | 'CANCELED'

export interface AiGenerationTask {
  taskId: string
  workspaceCode: string
  workspaceName: string
  createdByName?: string | null
  updatedByName?: string | null
  requirementTitle: string
  requirementContent: string
  outputMode: AiGenerationOutputMode
  status: AiGenerationTaskStatus
  currentStep: 1 | 2 | 3 | 4
  stepMessage: string
  errorMessage: string | null
  directoryId: number | null
  directoryName: string | null
  provider: string | null
  model: string | null
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
  createdAt: string | null
  updatedAt: string | null
  finishedAt: string | null
}

export interface CreateAiGenerationTaskPayload {
  workspaceCode?: string
  requirementTitle: string
  requirementContent: string
  outputMode: AiGenerationOutputMode
  directoryId?: number | null
  directoryName?: string | null
  assetIds?: number[]
}

export interface UpdateAiGenerationTaskPayload {
  workspaceCode?: string
  directoryId?: number | null
  directoryName?: string | null
  generatedCases?: AiGeneratedCase[]
  adoptedCaseIndexes?: number[]
  deletedCaseIndexes?: number[]
  savedCaseCount?: number
}

export interface CaseDirectoryNode {
  id: number
  name: string
  workspaceCode: string
  workspaceName: string
  parentId: number | null
  children: CaseDirectoryNode[]
}

export interface CaseDirectoryWorkspace {
  workspaceCode: string
  workspaceName: string
  children: CaseDirectoryNode[]
}

export interface CreateCaseDirectoryPayload {
  workspaceCode?: string
  parentId?: number | null
  name: string
}

export interface RenameCaseDirectoryPayload {
  name: string
}

export interface MoveCaseDirectoryPayload {
  targetParentId?: number | null
}

export interface BatchMoveCasesPayload {
  caseIds: number[]
  targetDirectoryId?: number | null
}

export interface BatchUpdateCasesPayload {
  caseIds: number[]
  priority?: string
  reviewStatus?: string
  executionStatus?: string
}

export interface BatchDeleteCasesPayload {
  caseIds: number[]
}

export interface TaskItem {
  id: number
  taskName: string
  engineType: string
  status: string
  summary: string
  workspaceCode: string
  workspaceName: string
}

export interface TaskDetail extends TaskItem {
  createdAt: string
  updatedAt: string
  reports: ReportItem[]
}

export interface CreateTaskPayload {
  workspaceCode?: string
  taskName: string
  engineType: string
  status: string
  summary: string
}

export interface TaskTransitionPayload {
  toStatus: string
}

export interface ReportItem {
  id: number
  taskId: number
  reportName: string
  result: string
  logSource: string
  workspaceCode: string
  workspaceName: string
  failureSummary: string
}

export interface ReportAttachment {
  id: number
  fileName: string
  contentType: string | null
  fileSize: number | null
  downloadUrl: string | null
  createdAt: string | null
}

export interface ReportDetail extends ReportItem {
  taskName: string
  logText: string | null
  attachments: ReportAttachment[]
  createdAt: string
  updatedAt: string
}

export interface ApiKeyValue {
  key: string
  value: string
  description?: string
  enabled?: boolean
  paramType?: string
  required?: boolean
  encode?: boolean
  minLength?: number | null
  maxLength?: number | null
}

export interface ApiAuthConfig {
  authType: 'NONE' | 'BASIC' | 'DIGEST'
  basicAuth: ApiAuthCredential
  digestAuth: ApiAuthCredential
}

export interface ApiAuthCredential {
  userName: string
  password: string
  valid?: boolean
}

export interface ApiRequestBodyConfig {
  type: string
  rawText?: string | null
  formItems: ApiKeyValue[]
  contentType?: string | null
  fileName?: string | null
  binaryBase64?: string | null
  jsonText?: string | null
  xmlText?: string | null
  plainText?: string | null
}

export type ApiAssertionType =
  | 'RESPONSE_CODE'
  | 'RESPONSE_HEADER'
  | 'RESPONSE_BODY'
  | 'RESPONSE_TIME'
  | 'VARIABLE'
  | 'SCRIPT'

export type ApiAssertionCondition =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'CONTAINS'
  | 'NOT_CONTAINS'
  | 'EMPTY'
  | 'NOT_EMPTY'
  | 'START_WITH'
  | 'END_WITH'
  | 'REGEX'
  | 'GT'
  | 'GT_OR_EQUALS'
  | 'LT'
  | 'LT_OR_EQUALS'
  | 'LENGTH_EQUALS'
  | 'LENGTH_NOT_EQUALS'
  | 'LENGTH_GT'
  | 'LENGTH_GT_OR_EQUALS'
  | 'LENGTH_LT'
  | 'LENGTH_LT_OR_EQUALS'
  | 'UNCHECKED'

export interface ApiAssertionItemConfig {
  header?: string
  expression?: string
  variableName?: string
  condition?: ApiAssertionCondition | string
  expectedValue?: string
  enabled?: boolean
}

export interface ApiAssertionGroupConfig {
  assertions: ApiAssertionItemConfig[]
  responseFormat?: 'XML' | 'HTML'
}

export interface ApiAssertionConfig {
  type?: string
  subject?: string
  operator?: string
  expectedValue?: string
  id?: string
  assertionType?: ApiAssertionType
  name?: string
  enabled?: boolean
  description?: string | null
  condition?: ApiAssertionCondition | string
  assertions?: ApiAssertionItemConfig[]
  assertionBodyType?: 'JSON_PATH' | 'X_PATH' | 'REGEX'
  jsonPathAssertion?: ApiAssertionGroupConfig
  xpathAssertion?: ApiAssertionGroupConfig
  regexAssertion?: ApiAssertionGroupConfig
  variableAssertionItems?: ApiAssertionItemConfig[]
  scriptLanguage?: 'JAVASCRIPT'
  script?: string
}

export interface ApiExtractorConfig {
  name: string
  sourceType: string
  expression: string
}

export interface ApiProcessorExtractorConfig {
  name: string
  sourceType?: 'BODY_JSONPATH' | 'HEADER' | 'STATUS_CODE'
  variableName?: string
  description?: string
  variableType?: 'TEMPORARY' | 'ENVIRONMENT'
  extractType?: 'JSON_PATH' | 'X_PATH' | 'REGEX'
  extractScope?: 'BODY' | 'UNESCAPED_BODY' | 'BODY_AS_DOCUMENT' | 'URL' | 'REQUEST_HEADERS' | 'RESPONSE_HEADERS' | 'RESPONSE_CODE' | 'RESPONSE_MESSAGE'
  expression: string
  expressionMatchingRule?: 'EXPRESSION' | 'GROUP'
  resultMatchingRule?: 'RANDOM' | 'SPECIFIC' | 'ALL'
  resultMatchingRuleNum?: number
  responseFormat?: 'JSON' | 'XML' | 'HTML'
  enabled?: boolean
}

export interface ApiProcessorBaseConfig {
  id: string
  processorType: 'SCRIPT' | 'SQL' | 'TIME_WAITING' | 'EXTRACT'
  name: string
  enabled: boolean
  description?: string | null
}

export interface ApiScriptProcessorConfig extends ApiProcessorBaseConfig {
  processorType: 'SCRIPT'
  scriptLanguage?: 'JAVASCRIPT'
  script: string
  delayMs?: number | null
  extractors?: ApiProcessorExtractorConfig[]
}

export interface ApiSqlExtractParam {
  key: string
  value: string
  description?: string
  enabled?: boolean
}

export interface ApiSqlProcessorConfig extends ApiProcessorBaseConfig {
  processorType: 'SQL'
  script: string
  dataSourceId?: number | null
  dataSourceName?: string | null
  queryTimeout?: number | null
  variableNames?: string | null
  extractParams?: ApiSqlExtractParam[]
  resultVariable?: string | null
  delayMs?: number | null
  extractors?: ApiProcessorExtractorConfig[]
}

export interface ApiWaitProcessorConfig extends ApiProcessorBaseConfig {
  processorType: 'TIME_WAITING'
  script?: string | null
  delayMs: number
  extractors?: ApiProcessorExtractorConfig[]
}

export interface ApiExtractProcessorConfig extends ApiProcessorBaseConfig {
  processorType: 'EXTRACT'
  script?: string | null
  delayMs?: number | null
  extractors: ApiProcessorExtractorConfig[]
}

export type ApiProcessorConfig =
  | ApiScriptProcessorConfig
  | ApiSqlProcessorConfig
  | ApiWaitProcessorConfig
  | ApiExtractProcessorConfig

export interface ApiRequestConfig {
  method: string
  path: string
  timeoutMs?: number | null
  queryParams: ApiKeyValue[]
  headers: ApiKeyValue[]
  cookies: ApiKeyValue[]
  body: ApiRequestBodyConfig
  authConfig: ApiAuthConfig
}

export interface ApiDefinitionItem {
  id: number
  workspaceCode: string
  workspaceName: string
  name: string
  method: string
  path: string
  directoryName: string | null
  description: string | null
  tags: string[]
  lastRunResult: string | null
  lastRunAt: string | null
  updatedAt: string | null
}

export interface ApiDefinitionDetail extends ApiDefinitionItem {
  requestConfig: ApiRequestConfig
  assertions: ApiAssertionConfig[]
  extractors: ApiExtractorConfig[]
  preProcessors: ApiProcessorConfig[]
  postProcessors: ApiProcessorConfig[]
  createdAt: string | null
}

export interface ApiScenarioStep {
  stepName: string
  definitionId: number
  enabled?: boolean
}

export interface ApiScenarioItem {
  id: number
  workspaceCode: string
  workspaceName: string
  name: string
  directoryName: string | null
  description: string | null
  tags: string[]
  stepCount: number
  defaultEnvironmentId: number | null
  variableSetId: number | null
  continueOnFailure: boolean
  lastRunResult: string | null
  lastRunAt: string | null
  updatedAt: string | null
}

export interface ApiScenarioDetail extends ApiScenarioItem {
  relatedCaseId: number | null
  steps: ApiScenarioStep[]
  createdAt: string | null
}

export interface ApiEnvironmentItem {
  id: number
  workspaceCode: string
  workspaceName: string
  name: string
  baseUrl: string
  headers: ApiKeyValue[]
  authConfig: ApiAuthConfig
  timeoutMs: number
  status: number
}

export interface ApiVariableItem {
  name: string
  value: string
  sensitive?: boolean
}

export interface ApiVariableSetItem {
  id: number
  workspaceCode: string
  workspaceName: string
  name: string
  variables: ApiVariableItem[]
  status: number
}

export interface ApiRequestSnapshot {
  method: string
  url: string
  headers: Record<string, string>
  body: string | null
}

export interface ApiResponseSnapshot {
  statusCode: number
  headers: Record<string, string>
  body: string | null
  contentType: string | null
}

export interface ApiAssertionResult {
  id?: string | null
  type: string
  name?: string | null
  subject: string
  condition?: string | null
  expectedValue?: string | null
  actualValue?: string | null
  success: boolean
  message: string
}

export interface ApiExtractionResult {
  name: string
  success: boolean
  value: string | null
  message: string
}

export interface ApiProcessorResult {
  stage: 'PRE' | 'POST'
  processorType: string
  name: string
  success: boolean
  durationMs: number
  message: string
  logs: string[]
  outputVariables: Record<string, string>
}

export interface ApiRunStepResult {
  id: number | null
  reportId: number | null
  stepOrder: number
  stepName: string
  definitionId: number | null
  success: boolean
  durationMs: number
  request: ApiRequestSnapshot | null
  response: ApiResponseSnapshot | null
  assertionResults: ApiAssertionResult[]
  extractionResults: ApiExtractionResult[]
  processorResults: ApiProcessorResult[]
  errorMessage: string | null
  createdAt: string | null
}

export interface ApiRunResponse {
  taskId: number
  reportId: number
  taskName: string
  reportName: string
  result: string
  failureSummary: string | null
  stepResults: ApiRunStepResult[]
}

export interface SaveApiDefinitionPayload {
  workspaceCode?: string
  name: string
  directoryName?: string | null
  description?: string | null
  tags: string[]
  requestConfig: ApiRequestConfig
  assertions: ApiAssertionConfig[]
  extractors: ApiExtractorConfig[]
  preProcessors: ApiProcessorConfig[]
  postProcessors: ApiProcessorConfig[]
}

export interface SaveApiScenarioPayload {
  workspaceCode?: string
  name: string
  directoryName?: string | null
  description?: string | null
  tags: string[]
  defaultEnvironmentId?: number | null
  variableSetId?: number | null
  continueOnFailure: boolean
  relatedCaseId?: number | null
  steps: ApiScenarioStep[]
}

export interface SaveApiEnvironmentPayload {
  workspaceCode?: string
  name: string
  baseUrl: string
  headers: ApiKeyValue[]
  authConfig: ApiAuthConfig
  timeoutMs: number
  status?: number
}

export interface SaveApiVariableSetPayload {
  workspaceCode?: string
  name: string
  variables: ApiVariableItem[]
  status?: number
}

export interface ApiRunPayload {
  workspaceCode?: string
  environmentId?: number | null
  variableSetId?: number | null
}

export interface ApiDebugDefinitionPayload {
  workspaceCode?: string
  definitionId?: number | null
  name?: string
  requestConfig: ApiRequestConfig
  assertions: ApiAssertionConfig[]
  extractors: ApiExtractorConfig[]
  preProcessors: ApiProcessorConfig[]
  postProcessors: ApiProcessorConfig[]
  environmentId?: number | null
  variableSetId?: number | null
}

export interface CreateReportPayload {
  workspaceCode?: string
  taskId: number | null
  reportName: string
  result: string
  logSource?: string
  failureSummary: string
}

export interface UpdateReportContentPayload {
  failureSummary: string
  logText: string
  logSource?: string
}

export interface UserItem {
  id: number
  username: string
  email: string
  displayName: string
  roleCode: string
  status: number
  workspaceCodes: string[]
  workspaceNames: string[]
}

export interface WorkspaceMemberItem {
  id: number
  userId: number
  username: string
  email: string
  displayName: string
  roleCode: string
  status: number
}

export interface EnvConfigItem {
  id: number
  workspaceCode: string
  workspaceName: string
  envType: string
  envName: string
  baseUrl: string
  configJson: string
  status: number
}

export interface ParamSetItem {
  id: number
  workspaceCode: string
  workspaceName: string
  paramType: string
  paramName: string
  contentJson: string
  status: number
}

export interface DbConnectionItem {
  id: number
  workspaceCode: string
  workspaceName: string
  connectionName: string
  dbType: 'MYSQL' | 'H2'
  driverClassName: string | null
  jdbcUrl: string
  username: string | null
  passwordConfigured: boolean
  poolMax: number
  timeoutMs: number
  description: string | null
  status: number
}

export interface CreateWorkspacePayload {
  workspaceCode: string
  workspaceName: string
  description: string
}

export interface CreateWorkspaceMemberPayload {
  userId: number | null
}

export interface BatchWorkspaceMemberPayload {
  userIds: number[]
}

export interface UpdateWorkspaceMemberPayload {
  roleCode: string
}

export interface CreateUserPayload {
  username: string
  email: string
  displayName: string
  roleCode: string
  workspaceCodes?: string[]
}

export interface UpdateUserPayload {
  email: string
  displayName: string
  roleCode: string
  status: number
  workspaceCodes?: string[]
}

export interface ResetPasswordResponse {
  userId: number
  username: string
  defaultPassword: string
}

export interface CreateEnvPayload {
  workspaceCode?: string
  envType: string
  envName: string
  baseUrl: string
  configJson: string
  status?: number
}

export interface CreateParamPayload {
  workspaceCode?: string
  paramType: string
  paramName: string
  contentJson: string
  status?: number
}

export interface CreateDbConnectionPayload {
  workspaceCode?: string
  connectionName: string
  dbType: 'MYSQL' | 'H2'
  driverClassName?: string | null
  jdbcUrl: string
  username?: string | null
  password?: string | null
  poolMax?: number
  timeoutMs?: number
  description?: string | null
  status?: number
}

export interface TestDbConnectionPayload extends Partial<CreateDbConnectionPayload> {
  id?: number | null
}

export interface DbConnectionTestResult {
  success: boolean
  message: string
}

export interface BugSummary {
  id: number
  bugNo: string
  title: string
  tags: string[]
  priority: string
  severity: string
  status: string
  assigneeName: string | null
  reporterName: string
  createdAt: string | null
  updatedByName: string | null
  updatedAt: string | null
  relatedCaseId: number | null
  relatedCaseCount: number
  workspaceCode: string
  workspaceName: string
}

export interface BugComment {
  id: number
  content: string
  commenterId: number
  commenterName: string
  createdAt: string
}

export interface BugAttachment {
  id: number
  fileName: string
  contentType: string | null
  fileSize: number | null
  downloadUrl: string | null
  uploadedByName: string | null
  createdAt: string | null
}

export interface BugFlow {
  id: number
  fromStatus: string
  toStatus: string
  operatorId: number
  operatorName: string
  actionComment: string
  createdAt: string
}

export type BugActivityType =
  | 'CREATED'
  | 'ASSIGNED'
  | 'STATUS_CHANGED'
  | 'COMMENT_ADDED'
  | 'ATTACHMENT_ADDED'
  | 'ATTACHMENT_REMOVED'
  | 'RELATION_UPDATED'

export interface BugCaseSummary {
  id: number
  caseNo: string
  title: string
  workspaceCode: string
  workspaceName: string
  directoryId: number | null
  directoryName: string | null
  modulePath: string
  executionStatus: string
  executionComment: string | null
  executedAt: string | null
}

export interface BugReportSummary {
  id: number
  reportName: string
  result: string
  failureSummary: string | null
  taskId: number | null
  taskName: string | null
  workspaceCode: string
  workspaceName: string
}

export interface BugTaskSummary {
  id: number
  taskName: string
  engineType: string
  status: string
  workspaceCode: string
  workspaceName: string
}

export interface BugSourceContext {
  sourceType: string
  caseSummary: BugCaseSummary | null
  reportSummary: BugReportSummary | null
  taskSummary: BugTaskSummary | null
}

export interface BugActivity {
  id: string
  type: BugActivityType
  operatorId: number | null
  operatorName: string | null
  occurredAt: string | null
  title: string
  content: string | null
  fromStatus: string | null
  toStatus: string | null
  attachmentId: number | null
  attachmentName: string | null
  commentId: number | null
}

export interface BugDetail extends BugSummary {
  description: string
  sourceType: string
  assigneeId: number | null
  reporterId: number
  relatedCaseId: number | null
  relatedReportId: number | null
  relatedTaskId: number | null
  tags: string[]
  attachments: BugAttachment[]
  sourceContext: BugSourceContext
  activities: BugActivity[]
  flows: BugFlow[]
  comments: BugComment[]
}

export interface BugStats {
  total: number
  todo: number
  assigned: number
  inProgress: number
  pendingVerify: number
  closed: number
  rejected: number
}

export interface CreateBugPayload {
  workspaceCode?: string
  title: string
  description: string
  priority: string
  severity: string
  assigneeId: number | null
  relatedCaseId?: number | null
  relatedReportId?: number | null
  relatedTaskId?: number | null
  tags: string[]
}

export interface UpdateBugPayload {
  workspaceCode?: string
  title: string
  description: string
  priority: string
  severity: string
  assigneeId: number | null
  relatedCaseId?: number | null
  tags: string[]
}
