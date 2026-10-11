import { useState } from 'react'
import { Briefcase, Check, Copy } from 'lucide-react'
import { PageHeader, Panel, Section } from '../components/Page'
import { StageTrack } from '../components/StageTrack'
import { Button } from '../components/ui'
import { useAuth } from '../lib/auth'

export default function Overview() {
  const { user } = useAuth()
  if (!user) return null
  return user.role === 'TPR' ? <TprOverview /> : <StudentOverview />
}

function StudentOverview() {
  return (
    <>
      <PageHeader title="Overview" subtitle="Your applications and the drives open to you." />

      <Section title="Your applications">
        <Panel>
          <StageTrack />
          <p className="mt-6 text-sm text-ink-soft">
            Once you apply to a drive, it shows up here and moves along these stages as the company updates it.
          </p>
        </Panel>
      </Section>

      <Section title="Open drives">
        <Panel className="flex flex-col items-center py-12 text-center">
          <span className="mb-4 grid size-12 place-items-center rounded-full bg-sunken text-ink-soft">
            <Briefcase className="size-5" aria-hidden="true" />
          </span>
          <p className="font-display text-lg font-semibold">No open drives yet</p>
          <p className="mt-1 max-w-sm text-sm text-ink-soft">
            Drives posted by your placement team appear here, with eligibility and the last date to apply.
          </p>
        </Panel>
      </Section>
    </>
  )
}

interface Step {
  title: string
  body: string
  state: 'done' | 'next' | 'later'
}

const STEPS: Step[] = [
  { title: 'Create your account', body: 'You are signed in as a placement representative.', state: 'done' },
  { title: 'Invite your batch', body: 'Students register with their own email. They do not need the invite code.', state: 'next' },
  { title: 'Post the first drive', body: 'Company, role, eligibility and the last date to apply.', state: 'later' },
  { title: 'Review applicants', body: 'See who applied, shortlist, and move people to the next round.', state: 'later' },
]

function TprOverview() {
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
                  {step.state === 'next' && <CopySignupLink />}
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
