const AUTH_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8081'
const NOTIFY_URL: string = import.meta.env.VITE_NOTIFY_API_URL ?? 'http://localhost:8083'
export const NOTIFY_STREAM_URL = `${import.meta.env.VITE_NOTIFY_API_URL ?? 'http://localhost:8083'}/api/notifications/stream`
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
}

export interface ProfileInput {
  cgpa: number | null
  course: string | null
  batch: string | null
}

export type DriveStatus = 'OPEN' | 'CLOSED'
export type Stage = 'APPLIED' | 'SHORTLISTED' | 'TEST' | 'INTERVIEW' | 'OFFER' | 'REJECTED'

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
  studentName: string
  studentEmail: string
  cgpa: number | null
  course: string | null
  batch: string | null
  stage: Stage
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
      const body = (await response.json()) as { message?: string; errors?: Record<string, string> }
      if (body.message) message = body.message
      if (body.errors) fieldErrors = body.errors
    } catch {
      // response had no JSON body, keep the default message
    }
    throw new ApiError(response.status, message, fieldErrors)
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
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

  // ---- drive-service ----
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
