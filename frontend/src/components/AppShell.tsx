import { NavLink, Outlet } from 'react-router-dom'
import { Briefcase, LayoutGrid, ListChecks, LogOut, Moon, Sun, TrendingUp, UserRound, Users, type LucideIcon } from 'lucide-react'
import { NotificationBell } from './NotificationBell'
import { Wordmark } from './Logo'
import { IconButton } from './ui'
import { useAuth } from '../lib/auth'
import { RealtimeProvider } from '../lib/realtime'
import { useTheme } from '../lib/theme'
import type { Role } from '../lib/api'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  end?: boolean
}

const NAV: Record<Role, NavItem[]> = {
  STUDENT: [
    { to: '/', label: 'Overview', icon: LayoutGrid, end: true },
    { to: '/drives', label: 'Drives', icon: Briefcase },
    { to: '/applications', label: 'Applications', icon: ListChecks },
    { to: '/profile', label: 'Profile', icon: UserRound },
  ],
  TPR: [
    { to: '/', label: 'Overview', icon: LayoutGrid, end: true },
    { to: '/drives', label: 'Drives', icon: Briefcase },
    { to: '/students', label: 'Students', icon: Users },
    { to: '/analytics', label: 'Analytics', icon: TrendingUp },
  ],
}

const ROLE_LABEL: Record<Role, string> = {
  STUDENT: 'Student',
  TPR: 'Placement team',
}

/** "Roshan Kumar" -> "RK", "Roshan" -> "R" */
function initials(fullName: string): string {
  const words = fullName.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) return '?'
  const first = words[0][0]
  const last = words.length > 1 ? words[words.length - 1][0] : ''
  return (first + last).toUpperCase()
}

export function AppShell() {
  const { user } = useAuth()
  if (!user) return null
  return (
    <RealtimeProvider>
      <Shell />
    </RealtimeProvider>
  )
}

function Shell() {
  const { user, logout } = useAuth()
  const { theme, toggle } = useTheme()
  if (!user) return null
  const items = NAV[user.role]

  return (
    <div className="min-h-dvh md:grid md:grid-cols-[15rem_1fr]">
      {/* Desktop rail */}
      <aside className="sticky top-0 hidden h-dvh flex-col bg-rail px-3 py-6 text-rail-ink md:flex">
        <Wordmark className="px-3" />
        <nav aria-label="Main" className="mt-10 flex flex-col gap-1">
          {items.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `flex h-10 items-center gap-3 rounded-lg px-3 text-[15px] transition-colors ${
                  isActive ? 'bg-white/12 font-semibold text-white' : 'text-rail-ink/75 hover:bg-white/8 hover:text-white'
                }`
              }
            >
              <Icon className="size-[18px]" aria-hidden="true" />
              {label}
            </NavLink>
          ))}
        </nav>

        <div className="mt-auto border-t border-white/10 px-3 pt-4">
          <div className="flex items-center gap-3">
            <span
              aria-hidden="true"
              className="grid size-9 shrink-0 place-items-center rounded-full bg-signal text-sm font-bold text-on-signal"
            >
              {initials(user.fullName)}
            </span>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold">{user.fullName}</p>
              <p className="truncate text-[13px] text-rail-ink/70">{ROLE_LABEL[user.role]}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={logout}
            className="mt-4 inline-flex items-center gap-2 rounded text-sm text-rail-ink/80 hover:text-white"
          >
            <LogOut className="size-4" aria-hidden="true" />
            Sign out
          </button>
        </div>
      </aside>

      <div className="flex min-h-dvh flex-col">
        <header className="flex items-center px-5 py-3 md:justify-end md:px-10">
          <Wordmark badge className="md:hidden" />
          <div className="ml-auto flex items-center gap-1 md:ml-0">
            <NotificationBell />
            <IconButton label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'} onClick={toggle}>
              {theme === 'dark' ? <Sun className="size-5" /> : <Moon className="size-5" />}
            </IconButton>
            <IconButton label="Sign out" onClick={logout} className="md:hidden">
              <LogOut className="size-5" />
            </IconButton>
          </div>
        </header>

        <main className="flex-1 px-5 pb-28 pt-2 md:px-10 md:pb-12">
          <div className="mx-auto max-w-4xl">
            <Outlet />
          </div>
        </main>
      </div>

      {/* Mobile tab bar: students mostly check drives from their phones */}
      <nav aria-label="Main" className="fixed inset-x-0 bottom-0 z-10 flex border-t border-line bg-surface md:hidden">
        {items.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              `flex flex-1 flex-col items-center gap-1 py-2.5 text-xs ${isActive ? 'font-semibold text-ink' : 'text-ink-soft'}`
            }
          >
            <Icon className="size-5" aria-hidden="true" />
            {label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
