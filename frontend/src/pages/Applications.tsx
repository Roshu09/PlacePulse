import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import { ListChecks } from 'lucide-react'
import { PageHeader, Panel } from '../components/Page'
import { StageTrack } from '../components/StageTrack'
import { Badge, Button, FormError } from '../components/ui'
import { api } from '../lib/api'
import { getToken } from '../lib/auth'
import { STAGE_LABEL, formatInstant, stageReached } from '../lib/format'
import { useLoad } from '../lib/useLoad'

export default function Applications() {
  const fetchApps = useCallback(() => api.myApplications(getToken()), [])
  const { data, error, loading, reload } = useLoad(fetchApps, 'applications')

  return (
    <>
      <PageHeader title="Applications" subtitle="Every drive you applied to, and how far each one has got." />

      {loading && !data ? (
        <p role="status" className="text-ink-soft">
          Loading applications…
        </p>
      ) : error && !data ? (
        <div className="space-y-3">
          <FormError>{error}</FormError>
          <Button variant="secondary" onClick={reload}>
            Try again
          </Button>
        </div>
      ) : data && data.length === 0 ? (
        <Panel className="flex flex-col items-center py-12 text-center">
          <span className="mb-4 grid size-12 place-items-center rounded-full bg-sunken text-ink-soft">
            <ListChecks className="size-5" aria-hidden="true" />
          </span>
          <p className="font-display text-lg font-semibold">No applications yet</p>
          <p className="mt-1 max-w-sm text-sm text-ink-soft">Apply to a drive and track it here as the company moves you through each round.</p>
          <Link
            to="/drives"
            className="mt-5 inline-flex h-10 items-center rounded-lg bg-signal px-4 text-sm font-semibold text-on-signal hover:bg-signal-strong"
          >
            Browse drives
          </Link>
        </Panel>
      ) : (
        <ul className="space-y-4">
          {data?.map((app) => (
            <li key={app.id}>
              <Panel>
                <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h3 className="font-display text-xl font-semibold leading-tight">{app.company}</h3>
                    <p className="text-ink-soft">{app.roleTitle}</p>
                  </div>
                  <div className="text-right">
                    <Badge tone={app.stage === 'OFFER' ? 'good' : app.stage === 'REJECTED' ? 'bad' : 'signal'}>
                      {STAGE_LABEL[app.stage]}
                    </Badge>
                    <p className="mt-1 text-xs text-ink-soft">Applied {formatInstant(app.appliedAt)}</p>
                  </div>
                </div>
                {app.stage === 'REJECTED' ? (
                  <p className="text-sm text-ink-soft">You were not selected for this drive. Keep going, the next one is on the Drives page.</p>
                ) : (
                  <StageTrack reached={stageReached(app.stage)} />
                )}
              </Panel>
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
