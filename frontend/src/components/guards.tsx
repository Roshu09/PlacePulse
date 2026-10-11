import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import type { Role } from '../lib/api'

function Splash() {
  return (
    <div className="grid min-h-dvh place-items-center text-ink-soft" role="status">
      Loading…
    </div>
  )
}

/** Only signed-in users get through; everyone else goes to the login page and comes back after. */
export function RequireAuth() {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <Splash />
  if (status === 'guest') return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}

/** Login and register are for signed-out visitors only. */
export function GuestOnly() {
  const { status } = useAuth()
  if (status === 'loading') return <Splash />
  if (status === 'authed') return <Navigate to="/" replace />
  return <Outlet />
}

/** Pages that only one role should open. Anyone else is sent back to the overview. */
export function RequireRole({ role }: { role: Role }) {
  const { user } = useAuth()
  if (user?.role !== role) return <Navigate to="/" replace />
  return <Outlet />
}
