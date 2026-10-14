import { useEffect, useRef, useState } from 'react'
import { Bell, CheckCheck } from 'lucide-react'
import type { AppNotification } from '../lib/api'
import { useRealtime } from '../lib/realtime'
import { timeAgo } from '../lib/format'

/**
 * The bell in the header. The data comes from the live connection (see lib/realtime), so new items
 * appear the moment they are created; the dot shows whether that connection is up.
 */
export function NotificationBell() {
  const { items, unread, loaded, failed, live, open: openNotification, markAllRead } = useRealtime()
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)

  // close on outside click or Escape
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (root.current && !root.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  function openItem(n: AppNotification) {
    setOpen(false)
    openNotification(n)
  }

  const markAll = markAllRead

  const label = unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'

  return (
    <div ref={root} className="relative">
      <button
        type="button"
        aria-label={label}
        title="Notifications"
        aria-expanded={open}
        aria-haspopup="dialog"
        onClick={() => setOpen((o) => !o)}
        className="relative inline-grid size-10 place-items-center rounded-lg text-ink-soft transition-colors hover:bg-sunken hover:text-ink"
      >
        <Bell className="size-5" aria-hidden="true" />
        {unread > 0 && (
          <span
            aria-hidden="true"
            className="absolute right-1 top-1 grid min-w-[18px] place-items-center rounded-full bg-signal px-1 text-[11px] font-bold leading-[18px] text-on-signal"
          >
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="Notifications"
          className="fixed inset-x-3 top-14 z-30 overflow-hidden rounded-xl bg-surface shadow-lg ring-1 ring-line sm:absolute sm:inset-x-auto sm:right-0 sm:top-12 sm:w-[24rem]"
        >
          <div className="flex items-center justify-between border-b border-line px-4 py-3">
            <h2 className="font-display text-base font-semibold">Notifications</h2>
            <span className="sr-only">{live ? 'Live updates on' : 'Live updates off, checking every few seconds'}</span>
            {unread > 0 && (
              <button type="button" onClick={markAll} className="inline-flex items-center gap-1.5 text-sm font-medium text-ink-soft hover:text-ink">
                <CheckCheck className="size-4" aria-hidden="true" /> Mark all read
              </button>
            )}
          </div>

          {!loaded ? (
            <p className="px-4 py-8 text-center text-sm text-ink-soft">Loading…</p>
          ) : items.length === 0 ? (
            <p className="px-4 py-10 text-center text-sm text-ink-soft">
              {failed ? "Can't reach notifications right now." : 'Nothing yet. Updates on drives and applications show up here.'}
            </p>
          ) : (
            <ul className="max-h-[26rem] divide-y divide-line overflow-y-auto">
              {items.map((n) => (
                <li key={n.id}>
                  <button
                    type="button"
                    onClick={() => openItem(n)}
                    className="flex w-full items-start gap-3 px-4 py-3 text-left hover:bg-sunken"
                  >
                    <span
                      aria-hidden="true"
                      className={`mt-1.5 size-2 shrink-0 rounded-full ${n.read ? 'bg-transparent' : 'bg-signal'}`}
                    />
                    <span className="min-w-0 flex-1">
                      <span className={`block text-sm ${n.read ? 'text-ink-soft' : 'font-semibold'}`}>
                        {n.title}
                        {!n.read && <span className="sr-only"> (unread)</span>}
                      </span>
                      <span className="mt-0.5 block text-[13px] text-ink-soft">{n.body}</span>
                      <span className="mt-1 block text-xs text-ink-soft/80">{timeAgo(n.createdAt)}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
