const BASE_URL: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8081'

export type Role = 'STUDENT' | 'TPR'

export interface User {
  id: string
  email: string
  fullName: string
  role: Role
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

async function request<T>(path: string, init: RequestInit = {}, token?: string | null): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, { ...init, headers })
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

  return (await response.json()) as T
}

export const api = {
  register: (input: RegisterInput) =>
    request<AuthResponse>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ ...input, inviteCode: input.inviteCode?.trim() || undefined }),
    }),

  login: (email: string, password: string) =>
    request<AuthResponse>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  me: (token: string) => request<User>('/api/users/me', {}, token),
}
