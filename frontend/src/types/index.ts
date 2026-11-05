// Mirrors backend DTOs 1:1 (see FRONTEND_PLAN.md §3). Do not diverge without updating the backend too.

export type Role = 'ADMIN' | 'CURATOR'
export type UserStatus = 'ACTIVE' | 'BLOCKED'
export type LeadStatus = 'ACTIVE' | 'ARCHIVED' | 'CONVERTED'
export type Health = 'green' | 'yellow' | 'red' | 'paused'

export interface AuthUserDto {
  id: string
  username: string
  fullName: string
  role: Role
  avatarColor: string | null
  seeLeads: boolean
  seeStats: boolean
  canReassign: boolean
  canDeleteStudents: boolean
}

export interface AuthResponse {
  accessToken: string
  user: AuthUserDto
}

export interface UserDto {
  id: string
  username: string
  fullName: string
  avatarColor: string | null
  role: Role
  status: UserStatus
  lastLoginAt: string | null
  createdAt: string
  seeLeads: boolean
  seeStats: boolean
  canReassign: boolean
  canDeleteStudents: boolean
}

export interface CreateUserRequest {
  username: string
  password: string
  fullName: string
  avatarColor?: string | null
  role: Role
  seeLeads?: boolean
  seeStats?: boolean
  canReassign?: boolean
  canDeleteStudents?: boolean
}

export interface UpdateUserRequest {
  fullName?: string
  avatarColor?: string | null
  seeLeads?: boolean
  seeStats?: boolean
  canReassign?: boolean
  canDeleteStudents?: boolean
}

export interface StageDto {
  id: string
  name: string
  position: number
  normDays: number | null
  isFinal: boolean
  isActive: boolean
}

export interface CreateStageRequest {
  name: string
  position: number
  normDays: number | null
  isFinal: boolean
}

export interface UpdateStageRequest {
  name?: string
  normDays?: number | null
  position?: number
  isActive?: boolean
}

export interface CohortDto {
  id: string
  name: string
  startDate: string
  archivedAt: string | null
}

export interface CreateCohortRequest {
  name: string
  startDate: string
}

export interface UpdateCohortRequest {
  name?: string
  startDate?: string
}

export interface LeadNoteDto {
  id?: string
  text: string
  position: number | null
}

export interface LeadDto {
  id: string
  name: string
  telegramUsername: string | null
  priceDescription: string | null
  postpayPercent: number | null
  nextPingAt: string | null
  status: LeadStatus
  assignedCuratorId: string | null
  createdById: string
  convertedStudentId: string | null
  archivedAt: string | null
  notes: LeadNoteDto[]
}

export interface CreateLeadRequest {
  name: string
  telegramUsername?: string
  priceDescription?: string
  postpayPercent?: number
  nextPingAt?: string
  curatorId?: string
  notes?: { text: string; position: number | null }[]
}

export type UpdateLeadRequest = Partial<CreateLeadRequest>

export interface ConvertLeadRequest {
  curatorId?: string
  cohortId?: string
  postpayPercent?: number
  startedAt?: string
}

export interface StudentNoteDto {
  id: string
  text: string
  position: number | null
}

export interface StudentDto {
  id: string
  fullName: string
  telegramUsername: string | null
  sourceLeadId: string | null
  currentStageId: string
  curatorId: string
  cohortId: string | null
  stageEnteredAt: string
  startedAt: string
  isPaused: boolean
  pausedAt: string | null
  postpayPercent: number | null
  createdById: string
  health: Health
  daysOnStage: number
  stagePosition: number
  notes: StudentNoteDto[]
  /** Changes when the Telegram photo changes; null = no photo (initials). */
  avatarVersion: string | null
}

export interface CreateStudentRequest {
  fullName: string
  telegramUsername?: string
  curatorId?: string
  cohortId?: string
  currentStageId: string
  stageEnteredAt?: string
  startedAt?: string
  postpayPercent?: number
}

export interface UpdateStudentRequest {
  fullName?: string
  telegramUsername?: string
  curatorId?: string
  stageEnteredAt?: string
  startedAt?: string
  postpayPercent?: number
  notes?: { text: string; position: number | null }[]
}

export interface StudentHistoryDto {
  stages: { stageId: string; enteredAt: string; exitedAt: string | null; changedById: string }[]
  curators: { fromCuratorId: string | null; toCuratorId: string; changedAt: string }[]
}

export interface CommentDto {
  id: string
  studentId: string
  authorId: string
  text: string
  createdAt: string
  updatedAt: string
}

export interface StatsOverview {
  total: number
  green: number
  yellow: number
  red: number
  paused: number
}

export interface StageStats {
  stageId: string
  name: string
  normDays: number | null
  /** Studying now; paused students (usually dropped out) are counted apart in `paused`. */
  active: number
  paused: number
  stuck: number
}

export interface CuratorStats {
  curatorId: string
  name: string
  avatarColor: string | null
  active: number
  paused: number
  stuck: number
  avgPct: number
}

export interface OverdueStudent {
  studentId: string
  name: string
  stageId: string
  stageName: string | null
  normDays: number | null
  curatorId: string
  curatorName: string | null
  daysOnStage: number
}

export interface CohortStats {
  cohortId: string
  name: string | null
  startDate: string | null
  total: number
  reachedCounts: number[]
  plannedStagePosition: number | null
  onTrackCount: number
  behindCount: number
}

export interface ApiError {
  status: number
  message: string
  errors?: Record<string, string>
}

export interface AuditLogDto {
  id: string
  createdAt: string
  actorId: string | null
  actorName: string
  entityType: string
  entityId: string
  entityName: string
  action: string
  description: string
}

export interface AuditPage {
  items: AuditLogDto[]
  total: number
  page: number
  size: number
}

export interface CuratorWorkload {
  curatorId: string
  name: string
  avatarColor: string | null
  blocked: boolean
  students: number
  green: number
  yellow: number
  red: number
  paused: number
  newLast30Days: number
  sharePct: number
}

/** The "Учебный сайт" tab of a student: login on the course site and progress through the course. */
export interface LearnAccessDto {
  /** False until the course site has been set up on the server. */
  connected: boolean
  /** Null until access is issued. */
  account: LearnAccountDto | null
  progress: LearnProgressDto | null
}

export interface LearnAccountDto {
  login: string
  /** As issued by the CRM; null once the student has set their own password. */
  password: string | null
  passwordChangedByStudent: boolean
  status: 'ACTIVE' | 'CLOSED'
  issuedAt: string
  lastSeenAt: string | null
}

export interface LearnProgressDto {
  completed: number
  total: number
  firstOpenedAt: string | null
  lastOpenedAt: string | null
  blocks: LearnBlockDto[]
}

export interface LearnBlockDto {
  id: string
  title: string
  order: number
  completed: number
  total: number
  lessons: LearnLessonDto[]
}

export interface LearnLessonDto {
  id: string
  title: string
  order: number
  openedAt: string | null
  completedAt: string | null
}
