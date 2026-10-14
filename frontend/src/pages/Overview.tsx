import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { Briefcase, Check, Copy } from 'lucide-react'
import { PageHeader, Panel, Section } from '../components/Page'
import { StageTrack } from '../components/StageTrack'
import { Button } from '../components/ui'
import { api } from '../lib/api'
import { getToken, useAuth } from '../lib/auth'
import { deadlineLabel, stageReached } from '../lib/format'
import { useLoad } from '../lib/useLoad'

export default function Overview() {
  const { user } = useAuth()
  if (!user) return null
  return user.role === 'TPR' ? <TprOverview /> : <StudentOverview />
}

function StudentOverview() {
  const { user } = useAuth()
  const fetchApps = useCallback(() => api.myApplications(getToken()), [])
  const fetchDrives = useCallback(() => api.drives(getToken()), [])
  const apps = useLoad(fetchApps, 'applications')
  const drives = useLoad(fetchDrives, 'drives')

  const latest = apps.data?.[0]
  const open = (drives.data ?? []).filter((d) => !d.applied)

  return (
    <>
      <PageHeader title="Overview" subtitle="Your applications and the drives open to you." />

      {user?.cgpa == null && (
        <Panel className="mb-8 flex flex-wrap items-center justify-between gap-3 border-l-4 !border-l-signal">
          <div>
            <p className="font-display text-lg font-semibold">Add your CGPA to start applying</p>
            <p className="text-sm text-ink-soft">Drives check it automatically. It takes a minute.</p>
          </div>
          <Link
            to="/profile"
            className="inline-flex h-10 items-center rounded-lg bg-signal px-4 text-sm font-semibold text-on-signal hover:bg-signal-strong"
          >
            Complete profile
          </Link>
        </Panel>
      )}

      <Section title="Your applications">
        <Panel>
          {latest ? (
            <>
              <div className="mb-5 flex flex-wrap items-baseline justify-between gap-2">
                <p className="font-medium">
                  {latest.company} <span className="text-ink-soft">· {latest.roleTitle}</span>
                </p>
                <Link to="/applications" className="text-sm font-medium underline underline-offset-4">
                  All applications ({apps.data?.length})
                </Link>
              </div>
              {latest.stage === 'REJECTED' ? (
                <p className="text-sm text-ink-soft">Not selected for this one. Other drives may still be open.</p>
              ) : (
                <StageTrack reached={stageReached(latest.stage)} />
              )}
            </>
          ) : (
            <>
              <StageTrack />
              <p className="mt-6 text-sm text-ink-soft">
                Once you apply to a drive, it shows up here and moves along these stages as the company updates it.
              </p>
            </>
          )}
        </Panel>
      </Section>

      <Section title="Open drives">
        {open.length === 0 ? (
          <Panel className="flex flex-col items-center py-12 text-center">
            <span className="mb-4 grid size-12 place-items-center rounded-full bg-sunken text-ink-soft">
              <Briefcase className="size-5" aria-hidden="true" />
            </span>
            <p className="font-display text-lg font-semibold">{drives.data?.length ? 'You have applied to every open drive' : 'No open drives yet'}</p>
            <p className="mt-1 max-w-sm text-sm text-ink-soft">
              Drives posted by your placement team appear here, with eligibility and the last date to apply.
            </p>
          </Panel>
        ) : (
          <Panel className="!p-0">
            <ul className="divide-y divide-line">
              {open.slice(0, 4).map((d) => (
                <li key={d.id} className="flex flex-wrap items-center justify-between gap-2 p-4 md:px-6">
                  <div className="min-w-0">
                    <p className="font-medium">{d.company}</p>
                    <p className="text-sm text-ink-soft">
                      {d.roleTitle} · CGPA {d.minCgpa.toFixed(2)}+
                    </p>
                  </div>
                  <span className="text-sm text-ink-soft">{deadlineLabel(d.deadline)}</span>
                </li>
              ))}
            </ul>
            <div className="border-t border-line p-4 md:px-6">
              <Link to="/drives" className="text-sm font-medium underline underline-offset-4">
                {open.length > 4 ? `See all ${open.length} drives` : 'Go to drives'}
              </Link>
            </div>
          </Panel>
        )}
      </Section>
    </>
  )
}

interface Step {
  title: string
  body: string
  state: 'done' | 'next' | 'later'
}

function buildSteps(drives: number, applicants: number): Step[] {
  const raw: Omit<Step, 'state'>[] = [
    { title: 'Create your account', body: 'You are signed in as a placement representative.' },
    { title: 'Invite your batch', body: 'Students register with their own email. They do not need the invite code.' },
    { title: 'Post the first drive', body: 'Company, role, eligibility and the last date to apply.' },
    { title: 'Review applicants', body: 'See who applied, shortlist, and move people to the next round.' },
  ]
  // "Invite your batch" can't be detected, so it counts as done once the first drive exists.
  const done = [true, drives > 0, drives > 0, applicants > 0]
  const firstOpen = done.indexOf(false)
  return raw.map((r, i) => ({ ...r, state: done[i] ? 'done' : i === firstOpen ? 'next' : 'later' }))
}

function TprOverview() {
  const fetchDrives = useCallback(() => api.drives(getToken()), [])
  const { data } = useLoad(fetchDrives, 'drives')
  const drives = data?.length ?? 0
  const applicants = (data ?? []).reduce((n, d) => n + (d.applicantCount ?? 0), 0)
  const STEPS = buildSteps(drives, applicants)
  return (
    <>
      <PageHeader title="Overview" subtitle="Set up your batch, then run every drive from one place." />

      <Section title="Getting started">
        <Panel className="!p-0">
          <ul className="divide-y divide-line">
            {STEPS.map((step) => (
              <li key={step.title} className="flex items-start gap-4 p-5 md:p-6">
                <span
                  aria-hidden="true"
                  className={`mt-0.5 grid size-6 shrink-0 place-items-center rounded-full ${
                    step.state === 'done' ? 'bg-good text-surface' : 'border-2 border-ink-soft/45'
                  }`}
                >
                  {step.state === 'done' && <Check className="size-3.5" strokeWidth={3} />}
                </span>
                <div className="min-w-0 flex-1">
                  <p className={`font-medium ${step.state === 'later' ? 'text-ink-soft' : ''}`}>
                    {step.title}
                    <span className="sr-only">{step.state === 'done' ? ' (done)' : ''}</span>
                  </p>
                  <p className="mt-0.5 text-sm text-ink-soft">{step.body}</p>
                  {step.state === 'next' && step.title === 'Invite your batch' && <CopySignupLink />}
                  {step.state === 'next' && step.title === 'Post the first drive' && (
                    <Link
                      to="/drives"
                      className="mt-3 inline-flex h-9 items-center rounded-lg bg-signal px-3.5 text-sm font-semibold text-on-signal hover:bg-signal-strong"
                    >
                      Post a drive
                    </Link>
                  )}
                  {step.state === 'next' && step.title === 'Review applicants' && (
                    <Link to="/drives" className="mt-3 inline-block text-sm font-medium underline underline-offset-4">
                      Open drives
                    </Link>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </Panel>
      </Section>
    </>
  )
}

function CopySignupLink() {
  const [copied, setCopied] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(`${window.location.origin}/register`)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      // clipboard blocked: leave the button as is
    }
  }

  return (
    <Button variant="secondary" onClick={copy} className="mt-3 h-9 text-sm">
      {copied ? <Check className="size-4" aria-hidden="true" /> : <Copy className="size-4" aria-hidden="true" />}
      {copied ? 'Copied' : 'Copy sign-up link'}
    </Button>
  )
}
