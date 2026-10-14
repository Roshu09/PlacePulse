import { Link } from 'react-router-dom'
import { CalendarClock, MapPin, Users, Wallet } from 'lucide-react'
import type { ReactNode } from 'react'
import { logoSrc, type Drive } from '../lib/api'
import { batchLabel } from '../lib/batches'
import { deadlineLabel, formatDate } from '../lib/format'
import { Badge } from './ui'

export function CompanyLogo({ drive, size = 'size-12' }: { drive: Drive; size?: string }) {
  const src = logoSrc(drive)
  return (
    <span aria-hidden="true" className={`grid ${size} shrink-0 place-items-center overflow-hidden rounded-lg bg-sunken font-display text-lg font-semibold text-ink-soft ring-1 ring-line`}>
      {src ? <img src={src} alt="" className="size-full object-contain" /> : drive.company.trim().charAt(0).toUpperCase()}
    </span>
  )
}

export function DriveMeta({ drive }: { drive: Drive }) {
  const urgent = drive.status === 'OPEN' && drive.acceptingApplications && deadlineLabelDays(drive.deadline)
  return (
    <ul className="mt-3 flex flex-wrap gap-x-5 gap-y-1.5 text-sm text-ink-soft">
      {drive.packageLpa != null && (
        <li className="inline-flex items-center gap-1.5">
          <Wallet className="size-4" aria-hidden="true" /> {drive.packageLpa} LPA
        </li>
      )}
      {drive.location && (
        <li className="inline-flex items-center gap-1.5">
          <MapPin className="size-4" aria-hidden="true" /> {drive.location}
        </li>
      )}
      <li className={`inline-flex items-center gap-1.5 ${urgent ? 'font-semibold text-bad' : ''}`}>
        <CalendarClock className="size-4" aria-hidden="true" /> Apply by {formatDate(drive.deadline)} · {deadlineLabel(drive.deadline)}
      </li>
    </ul>
  )
}

function deadlineLabelDays(iso: string) {
  const label = deadlineLabel(iso)
  return label === 'Last day today' || label === '1 day left'
}

export function Requirement({ drive }: { drive: Drive }) {
  return (
    <p className="mt-3 text-sm">
      <span className="text-ink-soft">Eligibility: </span>
      {drive.minCgpa === 0 ? 'No minimum CGPA' : `CGPA ${drive.minCgpa.toFixed(2)}+`} · {batchLabel(drive.eligibleBatches)}
    </p>
  )
}

interface Props {
  drive: Drive
  actions?: ReactNode
  /** TPR cards link through to the applicants page. */
  linkToApplicants?: boolean
}

export function DriveCard({ drive, actions, linkToApplicants }: Props) {
  const closed = drive.status === 'CLOSED'
  const expired = !closed && !drive.acceptingApplications

  return (
    <article className="rounded-xl bg-surface p-5 ring-1 ring-line md:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 items-center gap-3">
          <CompanyLogo drive={drive} />
          <div className="min-w-0">
            <h3 className="font-display text-xl font-semibold leading-tight">{drive.company}</h3>
            <p className="mt-0.5 text-ink-soft">{drive.roleTitle}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {drive.applicantCount != null && (
            <Badge>
              <Users className="mr-1 size-3.5" aria-hidden="true" />
              {drive.applicantCount} applied
            </Badge>
          )}
          {closed ? <Badge tone="bad">Closed</Badge> : expired ? <Badge tone="bad">Deadline passed</Badge> : <Badge tone="good">Open</Badge>}
        </div>
      </div>

      <DriveMeta drive={drive} />
      <Requirement drive={drive} />
      {drive.description && <p className="mt-3 whitespace-pre-line text-sm text-ink-soft">{drive.description}</p>}

      {(actions || linkToApplicants) && (
        <div className="mt-5 flex flex-wrap items-center gap-3 border-t border-line pt-4">
          {linkToApplicants && (
            <Link
              to={`/drives/${drive.id}`}
              className="inline-flex h-10 items-center rounded-lg bg-signal px-4 text-sm font-semibold text-on-signal hover:bg-signal-strong"
            >
              View applicants
            </Link>
          )}
          {actions}
        </div>
      )}
    </article>
  )
}
