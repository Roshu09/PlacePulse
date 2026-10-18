const AUTH_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8081'
const NOTIFY_URL: string = import.meta.env.VITE_NOTIFY_API_URL ?? 'http://localhost:8083'
export const NOTIFY_STREAM_URL = `${import.meta.env.VITE_NOTIFY_API_URL ?? 'http://localhost:8083'}/api/notifications/stream`
const AI_URL: string = import.meta.env.VITE_AI_API_URL ?? 'http://localhost:8084'
const DRIVE_URL: string = import.meta.env.VITE_DRIVE_API_URL ?? 'http://localhost:8082'

export type Role = 'STUDENT' | 'TPR'

export interface User {
  id: string
  email: string
  fullName: string
  role: Role
  cgpa: number | null
  course: string | null
  batch: string | null
  phone: string | null
  tenthPercent: number | null
  twelfthPercent: number | null
  resumeUrl: string | null
  dateOfBirth: string | null
  gender: string | null
  city: string | null
  graduationDegree: string | null
  graduationPercent: number | null
  graduationUniversity: string | null
  activeBacklogs: number | null
  githubUrl: string | null
  linkedinUrl: string | null
  portfolioUrl: string | null
  skills: string | null
  projects: string | null
  certifications: string | null
  placementRegistered: boolean
  placementRegisteredAt: string | null
  /** Keys of required profile fields that are still empty (students only). */
  missingRequired: string[]
}

/** The editable part of a profile. A null clears that field. */
export type ProfileFields = Pick<
  User,
  | 'cgpa' | 'course' | 'batch' | 'phone' | 'tenthPercent' | 'twelfthPercent' | 'resumeUrl'
  | 'dateOfBirth' | 'gender' | 'city' | 'graduationDegree' | 'graduationPercent' | 'graduationUniversity'
  | 'activeBacklogs' | 'githubUrl' | 'linkedinUrl' | 'portfolioUrl' | 'skills' | 'projects' | 'certifications'
>

export interface ProfileInput extends ProfileFields {
  /** Must be true to edit a profile that is already registered for placements. */
  acknowledgeDebar?: boolean
}

export type DriveStatus = 'OPEN' | 'CLOSED'
export type Stage = 'APPLIED' | 'SHORTLISTED' | 'TEST' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'DEBARRED'

export interface Drive {
  id: string
  company: string
  roleTitle: string
  description: string | null
  location: string | null
  packageLpa: number | null
  minCgpa: number
  eligibleBatches: number[] // pass-out years; empty means every batch
  logoUrl: string | null // relative to the drive service
  deadline: string // yyyy-mm-dd
  status: DriveStatus
  acceptingApplications: boolean
  applicantCount: number | null // TPR only
  applied: boolean | null // student only
  createdAt: string
}

export interface ResumeInfo {
  uploaded: boolean
  filename?: string
  uploadedAt?: string
  characters?: number
  sections?: number
}

export interface MatchResult {
  driveId: string
  /** 0-100. The AI's score when aiGenerated, otherwise a plain text-similarity score. */
  score: number
  similarity: number
  summary: string
  matchedSkills: string[]
  missingSkills: string[]
  suggestions: string[]
  aiGenerated: boolean
  notice: string | null
  cached: boolean
}

export interface RankedApplicant {
  studentId: string
  studentName: string | null
  stage: Stage | null
  hasResume: boolean
  similarity: number | null
  aiScore: number | null
  score: number | null
}

export interface DriveInput {
  company: string
  roleTitle: string
  description: string | null
  location: string | null
  packageLpa: number | null
  minCgpa: number
  eligibleBatches: number[]
  deadline: string
  removeLogo?: boolean // edit only
}

export interface Application {
  id: string
  driveId: string
  company: string
  roleTitle: string
  studentId: string
  studentName: string
  studentEmail: string
  cgpa: number | null
  course: string | null
  batch: string | null
  stage: Stage
  /** For a DEBARRED application: the stage to restore when reinstating. */
  debarredFrom: Stage | null
  appliedAt: string
  updatedAt: string
}

export interface AppNotification {
  id: string
  type: string
  title: string
  body: string
  link: string | null
  read: boolean
  createdAt: string
}

export interface NotificationsResponse {
  unreadCount: number
  items: AppNotification[]
}

export interface AuthResponse {
  token: string
  user: User
}

export interface RegisterInput {
  fullName: string
  email: string
  password: string
  inviteCode?: string
}

export class ApiError extends Error {
  status: number
  fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function request<T>(base: string, path: string, init: RequestInit = {}, token?: string | null): Promise<T> {
  const headers = new Headers(init.headers)
  // a FormData body sets its own Content-Type (with the multipart boundary)
  if (typeof init.body === 'string') headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(`${base}${path}`, { ...init, headers })
  } catch {
    throw new ApiError(0, "Can't reach the server. Check that the backend is running and try again.")
  }

  if (!response.ok) {
    let message = 'Something went wrong. Try again.'
    let fieldErrors: Record<string, string> = {}
    try {
      const body = (await response.json()) as { message?: string; detail?: unknown; errors?: Record<string, string> }
      if (body.message) message = body.message
      // ai-service (FastAPI) puts its message in `detail`
      else if (typeof body.detail === 'string') message = body.detail
      if (body.errors) fieldErrors = body.errors
    } catch {
      // response had no JSON body, keep the default message
    }
    throw new ApiError(response.status, message, fieldErrors)
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export interface Analytics {
  totals: {
    drives: number
    openDrives: number
    applications: number
    studentsApplied: number
    studentsPlaced: number
    offers: number
    placementRate: number
    averageCgpaApplicants: number | null
    averageCgpaPlaced: number | null
    averagePackageLpa: number | null
    highestPackageLpa: number | null
  }
  funnel: Record<string, number>
  drives: {
    driveId: string
    company: string
    roleTitle: string
    status: string
    deadline: string
    applicants: number
    shortlisted: number
    offers: number
    conversion: number
  }[]
  timeline: { date: string; applications: number }[]
  generatedAt: string
}

/** The analytics plus how the server produced them (from the X-Cache and X-Compute-Ms headers). */
export interface AnalyticsResult {
  data: Analytics
  cache: 'HIT' | 'MISS' | null
  serverMs: number | null
}

async function fetchAnalytics(token: string): Promise<AnalyticsResult> {
  let response: Response
  try {
    response = await fetch(`${DRIVE_URL}/api/analytics`, { headers: { Authorization: `Bearer ${token}` } })
  } catch {
    throw new ApiError(0, "Can't reach the server. Check that the backend is running and try again.")
  }
  if (!response.ok) throw new ApiError(response.status, 'Could not load analytics. Try again.')
  const cache = response.headers.get('X-Cache')
  const ms = response.headers.get('X-Compute-Ms')
  return {
    data: (await response.json()) as Analytics,
    cache: cache === 'HIT' || cache === 'MISS' ? cache : null,
    serverMs: ms === null ? null : Number(ms),
  }
}

/** Where to load a drive's logo from, or null when it has none. */
export function logoSrc(drive: Pick<Drive, 'logoUrl'>): string | null {
  return drive.logoUrl ? `${DRIVE_URL}${drive.logoUrl}` : null
}

/** The drive as JSON plus an optional PNG, the way drive-service expects it. */
function driveForm(input: DriveInput, logo: File | null): FormData {
  const form = new FormData()
  form.append('drive', new Blob([JSON.stringify(input)], { type: 'application/json' }))
  if (logo) form.append('logo', logo)
  return form
}

export const api = {
  register: (input: RegisterInput) =>
    request<AuthResponse>(AUTH_URL, '/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ ...input, inviteCode: input.inviteCode?.trim() || undefined }),
    }),

  login: (email: string, password: string) =>
    request<AuthResponse>(AUTH_URL, '/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  me: (token: string) => request<User>(AUTH_URL, '/api/users/me', {}, token),

  updateProfile: (token: string, input: ProfileInput) =>
    request<User>(AUTH_URL, '/api/users/me/profile', { method: 'PUT', body: JSON.stringify(input) }, token),

  registerForPlacements: (token: string) =>
    request<User>(AUTH_URL, '/api/users/me/placement-registration', { method: 'POST' }, token),

  // ---- ai-service ----
  resumeInfo: (token: string) => request<ResumeInfo>(AI_URL, '/api/resume', {}, token),

  uploadResume: (token: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return request<ResumeInfo>(AI_URL, '/api/resume', { method: 'POST', body: form }, token)
  },

  deleteResume: (token: string) => request<void>(AI_URL, '/api/resume', { method: 'DELETE' }, token),

  matchDrive: (token: string, driveId: string) =>
    request<MatchResult>(AI_URL, `/api/matches/drives/${driveId}`, { method: 'POST' }, token),

  rankApplicants: (token: string, driveId: string) =>
    request<RankedApplicant[]>(AI_URL, `/api/matches/drives/${driveId}/applicants`, {}, token),

  // ---- drive-service ----
  analytics: (token: string) => fetchAnalytics(token),

  drives: (token: string) => request<Drive[]>(DRIVE_URL, '/api/drives', {}, token),

  drive: (token: string, id: string) => request<Drive>(DRIVE_URL, `/api/drives/${id}`, {}, token),

  createDrive: (token: string, input: DriveInput, logo: File | null = null) =>
    request<Drive>(DRIVE_URL, '/api/drives', { method: 'POST', body: driveForm(input, logo) }, token),

  updateDrive: (token: string, id: string, input: DriveInput, logo: File | null = null) =>
    request<Drive>(DRIVE_URL, `/api/drives/${id}`, { method: 'PUT', body: driveForm(input, logo) }, token),

  /** Deletes one student's application so they can apply again. */
  removeApplication: (token: string, id: string) =>
    request<void>(DRIVE_URL, `/api/applications/${id}`, { method: 'DELETE' }, token),

  setDriveStatus: (token: string, id: string, status: DriveStatus) =>
    request<Drive>(DRIVE_URL, `/api/drives/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }, token),

  apply: (token: string, id: string) =>
    request<Application>(DRIVE_URL, `/api/drives/${id}/apply`, { method: 'POST' }, token),

  bulkSetStage: (token: string, ids: string[], stage: Stage) =>
    request<Application[]>(DRIVE_URL, '/api/applications/stage', { method: 'PATCH', body: JSON.stringify({ ids, stage }) }, token),

  /** Downloads a drive's applicants as a CSV file (the browser's save dialog / downloads folder). */
  downloadApplicantsCsv: async (token: string, id: string, filename: string) => {
    let response: Response
    try {
      response = await fetch(`${DRIVE_URL}/api/drives/${id}/applications.csv`, { headers: { Authorization: `Bearer ${token}` } })
    } catch {
      throw new ApiError(0, "Can't reach the server. Check that the backend is running and try again.")
    }
    if (!response.ok) throw new ApiError(response.status, 'Could not export the applicants. Try again.')
    const url = URL.createObjectURL(await response.blob())
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    URL.revokeObjectURL(url)
  },

  applicants: (token: string, id: string) =>
    request<Application[]>(DRIVE_URL, `/api/drives/${id}/applications`, {}, token),

  myApplications: (token: string) => request<Application[]>(DRIVE_URL, '/api/applications/mine', {}, token),

  setStage: (token: string, id: string, stage: Stage) =>
    request<Application>(DRIVE_URL, `/api/applications/${id}/stage`, { method: 'PATCH', body: JSON.stringify({ stage }) }, token),

  // ---- notification-service ----
  notifications: (token: string) => request<NotificationsResponse>(NOTIFY_URL, '/api/notifications', {}, token),

  markNotificationRead: (token: string, id: string) =>
    request<AppNotification>(NOTIFY_URL, `/api/notifications/${id}/read`, { method: 'POST' }, token),

  markAllNotificationsRead: (token: string) =>
    request<{ updated: number }>(NOTIFY_URL, '/api/notifications/read-all', { method: 'POST' }, token),
}
