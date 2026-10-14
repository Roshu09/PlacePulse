import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, NOTIFY_STREAM_URL, type AppNotification } from './api'
import { getToken, useAuth } from './auth'
import { openStream } from './sse'

/** A kind of page data that can go stale. Pages say which kinds they show; events say which kinds changed. */
export type Topic = 'drives' | 'applications' | 'applicants'

const ALL_TOPICS: Topic[] = ['drives', 'applications', 'applicants']

const TOPICS_BY_EVENT: Record<string, Topic[]> = {
  'drive.created': ['drives'],
  'drive.updated': ['drives', 'applications', 'applicants'],
  'application.submitted': ['applicants', 'drives'],
  'application.stage-changed': ['applications'],
  'application.removed': ['applications', 'drives', 'applicants'],
}

const FALLBACK_POLL_MS = 15_000
const MAX_ITEMS = 30
const TOAST_MS = 6_000
const MAX_TOASTS = 3

type Versions = Record<Topic, number>

interface RealtimeValue {
  items: AppNotification[]
  unread: number
  loaded: boolean
  failed: boolean
  /** True while the live connection is up. */
  live: boolean
  versions: Versions
  open: (n: AppNotification) => void
  markAllRead: () => void
}

const RealtimeContext = createContext<RealtimeValue | null>(null)

interface Toast {
  key: string
  n: AppNotification
}

export function RealtimeProvider({ children }: { children: ReactNode }) {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [items, setItems] = useState<AppNotification[]>([])
  const [unread, setUnread] = useState(0)
  const [loaded, setLoaded] = useState(false)
  const [failed, setFailed] = useState(false)
  const [live, setLive] = useState(false)
  const [versions, setVersions] = useState<Versions>({ drives: 0, applications: 0, applicants: 0 })
  const [toasts, setToasts] = useState<Toast[]>([])
  const known = useRef<Set<string>>(new Set())

  const bump = useCallback((topics: Topic[]) => {
    setVersions((v) => {
      const next = { ...v }
      for (const t of topics) next[t] += 1
      return next
    })
  }, [])

  const refresh = useCallback(async () => {
    try {
      const data = await api.notifications(getToken())
      known.current = new Set(data.items.map((i) => i.id))
      setItems(data.items)
      setUnread(data.unreadCount)
      setFailed(false)
    } catch {
      setFailed(true)
    } finally {
      setLoaded(true)
    }
  }, [])

  const onNotification = useCallback(
    (n: AppNotification) => {
      // the list and the unread count may already include this item if a refresh got there first
      if (!known.current.has(n.id)) {
        known.current.add(n.id)
        setItems((list) => [n, ...list].slice(0, MAX_ITEMS))
        if (!n.read) setUnread((c) => c + 1)
      }
      // but the person should still see the toast, and the page data still needs to reload
      bump(TOPICS_BY_EVENT[n.type] ?? ALL_TOPICS)
      setToasts((list) => (list.some((t) => t.key === n.id) ? list : [{ key: n.id, n }, ...list].slice(0, MAX_TOASTS)))
    },
    [bump],
  )

  // the live connection
  useEffect(() => {
    void refresh()
    return openStream(NOTIFY_STREAM_URL, getToken, {
      onOpen: (reconnect) => {
        setLive(true)
        if (reconnect) {
          // we may have missed things while disconnected: catch up
          void refresh()
          bump(ALL_TOPICS)
        }
      },
      onClose: () => setLive(false),
      onEvent: (event, data) => {
        if (event !== 'notification') return // "ready" and "ping" only keep the line open
        try {
          onNotification(JSON.parse(data) as AppNotification)
        } catch {
          void refresh()
        }
      },
      onUnauthorized: () => logout(),
    })
  }, [refresh, bump, onNotification, logout])

  // safety nets: while the live line is down, poll; whenever the tab is shown again, catch up
  useEffect(() => {
    if (live) return
    const timer = window.setInterval(() => {
      void refresh()
      bump(ALL_TOPICS)
    }, FALLBACK_POLL_MS)
    return () => window.clearInterval(timer)
  }, [live, refresh, bump])

  useEffect(() => {
    const catchUp = () => {
      if (document.visibilityState === 'hidden') return
      void refresh()
      bump(ALL_TOPICS)
    }
    window.addEventListener('focus', catchUp)
    document.addEventListener('visibilitychange', catchUp)
    return () => {
      window.removeEventListener('focus', catchUp)
      document.removeEventListener('visibilitychange', catchUp)
    }
  }, [refresh, bump])

  // toasts disappear on their own
  useEffect(() => {
    if (toasts.length === 0) return
    const timer = window.setTimeout(() => setToasts((list) => list.slice(0, -1)), TOAST_MS)
    return () => window.clearTimeout(timer)
  }, [toasts])

  const open = useCallback(
    (n: AppNotification) => {
      setToasts((list) => list.filter((t) => t.key !== n.id))
      if (!n.read) {
        setItems((list) => list.map((i) => (i.id === n.id ? { ...i, read: true } : i)))
        setUnread((c) => Math.max(0, c - 1))
        api.markNotificationRead(getToken(), n.id).catch(() => void refresh())
      }
      // the page we are going to may already be on screen (same route), so tell it to reload its data
      bump(TOPICS_BY_EVENT[n.type] ?? ALL_TOPICS)
      if (n.link) navigate(n.link)
    },
    [bump, navigate, refresh],
  )

  const markAllRead = useCallback(() => {
    setItems((list) => list.map((i) => ({ ...i, read: true })))
    setUnread(0)
    api.markAllNotificationsRead(getToken()).catch(() => void refresh())
  }, [refresh])

  const value = useMemo(
    () => ({ items, unread, loaded, failed, live, versions, open, markAllRead }),
    [items, unread, loaded, failed, live, versions, open, markAllRead],
  )

  return (
    <RealtimeContext.Provider value={value}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed inset-x-3 bottom-20 z-40 flex flex-col items-end gap-2 md:inset-x-auto md:bottom-6 md:right-6"
      >
        {toasts.map(({ key, n }) => (
          <button
            key={key}
            type="button"
            onClick={() => open(n)}
            className="pointer-events-auto w-full rounded-xl bg-surface p-4 text-left shadow-lg ring-1 ring-line hover:bg-sunken md:w-[22rem]"
          >
            <span className="block text-sm font-semibold">{n.title}</span>
            <span className="mt-0.5 block text-[13px] text-ink-soft">{n.body}</span>
          </button>
        ))}
      </div>
    </RealtimeContext.Provider>
  )
}

export function useRealtime(): RealtimeValue {
  const ctx = useContext(RealtimeContext)
  if (!ctx) throw new Error('useRealtime must be used inside <RealtimeProvider>')
  return ctx
}

/** A number that changes whenever `topic` data may have changed. Outside the provider it never changes. */
export function useLive(topic?: Topic): number {
  const ctx = useContext(RealtimeContext)
  return topic && ctx ? ctx.versions[topic] : 0
}
