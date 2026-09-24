export type UserStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED'

export interface CurrentUser {
  id: number
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}

export interface UserSummary {
  id: number
  username: string
  displayName: string
  email: string | null
  mobile: string | null
  status: UserStatus
  createdAt: string
  updatedAt: string
}

export interface Pagination {
  page: number
  pageSize: number
  totalItems: number
  totalPages: number
}

export interface PageResponse<T> {
  data: T[]
  pagination: Pagination
}

export interface LoginInput {
  username: string
  password: string
}

export interface ChangePasswordInput {
  currentPassword: string
  newPassword: string
}

export interface UserQuery {
  page: number
  pageSize: number
  keyword?: string
  status?: 'ACTIVE' | 'DISABLED'
}

export interface CreateUserInput {
  username: string
  initialPassword: string
  displayName: string
  email?: string
  mobile?: string
}

export type AuditOutcome = 'SUCCESS' | 'FAILURE'

export interface AuditActor {
  id: number
  username: string
  displayName: string
}

export interface AuditLogEntry {
  id: number
  actor: AuditActor | null
  action: string
  resourceType: string
  resourceId: string | null
  outcome: AuditOutcome
  ipAddress: string | null
  userAgent: string | null
  requestId: string | null
  details: Record<string, unknown>
  createdAt: string
}

export interface AuditLogQuery {
  page: number
  pageSize: number
  actorId?: number
  action?: string
  resourceType?: string
  outcome?: AuditOutcome
  createdFrom?: string
  createdTo?: string
}

export type ProjectStatus = 'PLANNING' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED'

export interface ProjectOwner {
  id: number
  displayName: string
}

export interface Project {
  id: number
  code: string
  name: string
  customerName: string | null
  description: string | null
  status: ProjectStatus
  owner: ProjectOwner
  startDate: string | null
  endDate: string | null
  tags: string[]
  createdAt: string
  updatedAt: string
}

export interface ProjectQuery {
  page: number
  pageSize: number
  keyword?: string
  status?: ProjectStatus
}

export interface ProjectUpdateInput {
  name: string
  customerName?: string
  status: ProjectStatus
  startDate?: string
  endDate?: string
  tags: string[]
  description?: string
}

export interface ProjectCreateInput extends ProjectUpdateInput {
  code: string
}

export type ProjectMemberRole = 'OWNER' | 'MANAGER' | 'MEMBER' | 'VIEWER'
export type EditableProjectMemberRole = Exclude<ProjectMemberRole, 'OWNER'>

export interface ProjectMember {
  userId: number
  username: string
  displayName: string
  status: UserStatus
  role: ProjectMemberRole
  joinedAt: string
}

export interface ProjectMemberQuery {
  page: number
  pageSize: number
  keyword?: string
}

export interface AddProjectMemberInput {
  userId: number
  role: EditableProjectMemberRole
}

export interface ProjectMemberCandidate {
  userId: number
  username: string
  displayName: string
}

export type FileAssetStatus = 'RESERVED' | 'UPLOADING' | 'AVAILABLE' | 'FAILED'

export interface FileAsset {
  id: number
  fileGroupId: string
  version: number
  originalName: string
  mediaType: string | null
  sizeBytes: number
  sha256: string | null
  status: FileAssetStatus
  uploadedBy: ProjectOwner
  createdAt: string
  chunkSizeBytes?: number | null
  totalChunks?: number | null
}

export interface FileAssetQuery {
  page: number
  pageSize: number
  keyword?: string
}

export interface CreateFileMetadataInput {
  originalName: string
  mediaType?: string
  sizeBytes: number
  sha256?: string
  fileGroupId?: string
}

export type DeploymentAssetType =
  | 'INSTALLATION_PACKAGE'
  | 'SCRIPT'
  | 'MANUAL'
  | 'CONFIG_TEMPLATE'
  | 'DEPENDENCY'

export type DeploymentEnvironment =
  | 'DEVELOPMENT'
  | 'TESTING'
  | 'STAGING'
  | 'PRODUCTION'
  | 'GENERAL'

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface DeploymentAsset {
  id: number
  assetGroupId: string
  version: number
  name: string
  assetType: DeploymentAssetType
  versionLabel: string
  file: {
    id: number
    originalName: string
    sizeBytes: number
    status: FileAssetStatus
  }
  operatingSystem: string | null
  architecture: string | null
  environment: DeploymentEnvironment
  riskLevel: RiskLevel
  tags: string[]
  description: string | null
  prerequisites: string | null
  executionInstructions: string | null
  rollbackInstructions: string | null
  createdBy: ProjectOwner
  createdAt: string
}

export interface DeploymentAssetQuery {
  page: number
  pageSize: number
  keyword?: string
  assetType?: DeploymentAssetType
  operatingSystem?: string
  architecture?: string
  environment?: DeploymentEnvironment
  riskLevel?: RiskLevel
  tag?: string
}

export interface CreateDeploymentAssetInput {
  assetGroupId?: string
  name: string
  assetType: DeploymentAssetType
  versionLabel: string
  fileAssetId: number
  operatingSystem?: string
  architecture?: string
  environment: DeploymentEnvironment
  riskLevel: RiskLevel
  tags: string[]
  description?: string
  prerequisites?: string
  executionInstructions?: string
  rollbackInstructions?: string
}

export type ServerStatus = 'ACTIVE' | 'MAINTENANCE' | 'RETIRED'

export interface ServerRecord {
  id: number
  projectId: number
  name: string
  host: string
  port: number
  environment: DeploymentEnvironment
  status: ServerStatus
  operatingSystem: string | null
  architecture: string | null
  purpose: string | null
  description: string | null
  credentialConfigured: boolean
  createdAt: string
  updatedAt: string
}

export interface ServerQuery {
  page: number
  pageSize: number
  keyword?: string
  environment?: DeploymentEnvironment
  status?: ServerStatus
}

export interface SaveServerInput {
  name: string
  host: string
  port: number
  environment: DeploymentEnvironment
  status: ServerStatus
  operatingSystem?: string
  architecture?: string
  purpose?: string
  description?: string
}

export interface SaveServerCredentialInput {
  username: string
  password: string
}

export interface ServerCredential {
  username: string
  password: string
}

export interface EnvironmentFingerprint {
  id: number
  projectId: number
  name: string
  environment: DeploymentEnvironment
  operatingSystem: string
  osVersion: string | null
  kernelVersion: string | null
  architecture: string
  runtimeName: string | null
  runtimeVersion: string | null
  databaseName: string | null
  databaseVersion: string | null
  middlewares: string[]
  networkZone: string | null
  tags: string[]
  notes: string | null
  createdAt: string
  updatedAt: string
}

export interface EnvironmentFingerprintQuery {
  page: number
  pageSize: number
  keyword?: string
  environment?: DeploymentEnvironment
  operatingSystem?: string
  architecture?: string
  databaseName?: string
  middleware?: string
  tag?: string
}

export interface SaveEnvironmentFingerprintInput {
  name: string
  environment: DeploymentEnvironment
  operatingSystem: string
  osVersion?: string
  kernelVersion?: string
  architecture: string
  runtimeName?: string
  runtimeVersion?: string
  databaseName?: string
  databaseVersion?: string
  middlewares: string[]
  networkZone?: string
  tags: string[]
  notes?: string
}

export type DeploymentSolutionStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED'

export interface DeploymentSolutionSummary {
  id: number
  projectId: number
  name: string
  scenario: string
  fingerprintId: number
  fingerprintName: string
  status: DeploymentSolutionStatus
  stepCount: number
  updatedAt: string
}

export interface DeploymentSolutionStep {
  id: number
  stepOrder: number
  title: string
  instructions: string
  assetId: number
  assetName: string
  assetVersion: number
  assetVersionLabel: string
  parametersTemplate: string | null
}

export interface DeploymentSolution extends Omit<DeploymentSolutionSummary, 'stepCount'> {
  architectureDescription: string
  prerequisites: string
  rollbackSteps: string
  riskNotes: string
  steps: DeploymentSolutionStep[]
  createdAt: string
}

export interface DeploymentSolutionQuery {
  page: number
  pageSize: number
  keyword?: string
  status?: DeploymentSolutionStatus
  fingerprintId?: number
}

export interface SaveDeploymentSolutionStepInput {
  stepOrder: number
  title: string
  instructions: string
  assetId: number
  parametersTemplate?: string
}

export interface SaveDeploymentSolutionInput {
  name: string
  scenario: string
  fingerprintId: number
  architectureDescription: string
  prerequisites: string
  rollbackSteps: string
  riskNotes: string
  status: DeploymentSolutionStatus
  steps: SaveDeploymentSolutionStepInput[]
}

export type DeploymentResult = 'SUCCESS' | 'FAILED'

export interface DeploymentRecordSummary {
  id: number
  projectId: number
  serverId: number
  serverName: string
  solutionId: number
  solutionName: string
  environmentFingerprintId: number
  result: DeploymentResult
  executedBy: number
  executedByName: string
  executedAt: string
  exceptionNotes: string | null
  notes: string | null
  baseline: boolean
  createdAt: string
}

export interface DeploymentRecord extends DeploymentRecordSummary {
  serverSnapshot: ServerRecord
  environmentSnapshot: EnvironmentFingerprint
  solutionSnapshot: DeploymentSolution
}

export interface DeploymentRecordQuery {
  page: number
  pageSize: number
  result?: DeploymentResult
  serverId?: number
  baseline?: boolean
  executedFrom?: string
  executedTo?: string
}

export interface CreateDeploymentRecordInput {
  serverId: number
  solutionId: number
  executedAt: string
  result: DeploymentResult
  exceptionNotes?: string
  notes?: string
}

export interface SimilarDeployment {
  record: DeploymentRecordSummary
  score: number
  matchedFields: string[]
  differentFields: string[]
}

export type WorkItemType = 'TASK' | 'MILESTONE'
export type WorkItemStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'CANCELED'
export type WorkItemPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
export type WorkItemRelationType = 'PARENT_CHILD' | 'PRECEDES' | 'BLOCKS'

export interface WorkItem {
  id: number
  projectId: number
  type: WorkItemType
  title: string
  description: string | null
  status: WorkItemStatus
  priority: WorkItemPriority
  assignee: ProjectOwner | null
  plannedStartDate: string | null
  plannedEndDate: string | null
  actualStartDate: string | null
  actualEndDate: string | null
  createdAt: string
  updatedAt: string
}

export interface WorkItemQuery {
  page: number
  pageSize: number
  keyword?: string
  type?: WorkItemType
  status?: WorkItemStatus
  priority?: WorkItemPriority
  assigneeId?: number
  plannedFrom?: string
  plannedTo?: string
}

export interface CreateWorkItemInput {
  type: WorkItemType
  title: string
  description?: string
  priority?: WorkItemPriority
  assigneeId?: number
  plannedStartDate?: string
  plannedEndDate?: string
}

export interface UpdateWorkItemInput {
  type?: WorkItemType
  title?: string
  description?: string | null
  priority?: WorkItemPriority
  assigneeId?: number | null
  plannedStartDate?: string | null
  plannedEndDate?: string | null
}

export interface TransitionWorkItemStatusInput {
  status: WorkItemStatus
  comment?: string
}

export interface WorkItemStatusLog {
  id: number
  fromStatus: WorkItemStatus | null
  toStatus: WorkItemStatus
  comment: string | null
  changedBy: ProjectOwner
  changedAt: string
}

export interface WorkItemRelationEndpoint {
  id: number
  title: string
  type: WorkItemType
  status: WorkItemStatus
}

export interface WorkItemRelation {
  id: number
  projectId: number
  type: WorkItemRelationType
  source: WorkItemRelationEndpoint
  target: WorkItemRelationEndpoint
  createdBy: ProjectOwner
  createdAt: string
}

export interface WorkItemRelationQuery {
  page: number
  pageSize: number
  workItemId?: number
  type?: WorkItemRelationType
}

export interface CreateWorkItemRelationInput {
  sourceWorkItemId: number
  targetWorkItemId: number
  type: WorkItemRelationType
}

export type WorkItemReminderStatus = 'PENDING' | 'DISMISSED'

export interface WorkItemReminder {
  id: number
  projectId: number
  workItem: WorkItemRelationEndpoint
  remindAt: string
  message: string | null
  status: WorkItemReminderStatus
  createdAt: string
  dismissedAt: string | null
}

export interface WorkItemReminderQuery {
  page: number
  pageSize: number
  workItemId?: number
  status?: WorkItemReminderStatus
  from?: string
  to?: string
}

export interface CreateWorkItemReminderInput {
  workItemId: number
  remindAt: string
  message?: string
}
