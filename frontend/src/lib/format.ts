import type { Stage } from './api'

/** "2026-11-05" -> "5 Nov 2026" (parsed as a plain calendar date, never shifted by time zone). */
export function formatDate(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

/** Whole days from today to a yyyy-mm-dd deadline (0 = today, negative = passed). */
export function daysLeft(iso: string): number {
  const [y, m, d] = iso.split('-').map(Number)
  const target = new Date(y, m - 1, d).getTime()
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  return Math.round((target - today) / 86_400_000)
}

export function deadlineLabel(iso: string): string {
  const n = daysLeft(iso)
  if (n < 0) return 'Deadline passed'
  if (n === 0) return 'Last day today'
  if (n === 1) return '1 day left'
  return `${n} days left`
}

export const STAGE_LABEL: Record<Stage, string> = {
  APPLIED: 'Applied',
  SHORTLISTED: 'Shortlisted',
  TEST: 'Test',
  INTERVIEW: 'Interview',
  OFFER: 'Offer',
  REJECTED: 'Not selected',
}

/** Stages in order, as the track shows them. REJECTED is a dead end and has no position. */
export const STAGE_ORDER: Stage[] = ['APPLIED', 'SHORTLISTED', 'TEST', 'INTERVIEW', 'OFFER']

export function stageReached(stage: Stage): number {
  return STAGE_ORDER.indexOf(stage) + 1
}

/** A timestamp from the server, shown as an Indian calendar date. */
export function formatInstant(iso: string): string {
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'Asia/Kolkata' })
}

/** "just now", "5 min ago", "3 h ago", "2 d ago", then a date. */
export function timeAgo(iso: string, now: number = Date.now()): string {
  const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000))
  if (seconds < 60) return 'just now'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days} d ago`
  return formatInstant(iso)
}
