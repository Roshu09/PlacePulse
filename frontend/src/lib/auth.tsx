import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, ApiError, type AuthResponse, type RegisterInput, type User } from './api'

const TOKEN_KEY = 'pp-token'

type Status = 'loading' | 'authed' | 'guest'

interface AuthContextValue {
  user: User | null
  status: Status
  login: (email: string, password: string) => Promise<void>
  register: (input: RegisterInput) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

function writeToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // storage unavailable (private mode): the session just won't survive a reload
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [status, setStatus] = useState<Status>('loading')

  // On first load, turn a saved token back into a user (or discard it if it has expired).
  useEffect(() => {
    const token = readToken()
    if (!token) {
      setStatus('guest')
      return
    }
    let cancelled = false
    api
      .me(token)
      .then((u) => {
        if (cancelled) return
        setUser(u)
        setStatus('authed')
      })
      .catch((error: unknown) => {
        if (cancelled) return
        if (error instanceof ApiError && error.status === 401) writeToken(null)
        setStatus('guest')
      })
    return () => {
      cancelled = true
    }
  }, [])

  const accept = useCallback((response: AuthResponse) => {
    writeToken(response.token)
    setUser(response.user)
    setStatus('authed')
  }, [])

  const login = useCallback(
    async (email: string, password: string) => accept(await api.login(email, password)),
    [accept],
  )

  const register = useCallback(
    async (input: RegisterInput) => accept(await api.register(input)),
    [accept],
  )

  const logout = useCallback(() => {
    writeToken(null)
    setUser(null)
    setStatus('guest')
  }, [])

  const value = useMemo(
    () => ({ user, status, login, register, logout }),
    [user, status, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
