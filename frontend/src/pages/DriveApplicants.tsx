import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Trash2 } from 'lucide-react'
import { DriveMeta, Requirement } from '../components/DriveCard'
import { PageHeader, Panel } from '../components/Page'
import { Button, FormError, Select } from '../components/ui'
import { ApiError, api, type Application, type Stage } from '../lib/api'
import { getToken } from '../lib/auth'
import { STAGE_LABEL } from '../lib/format'
import { useLoad } from '../lib/useLoad'

const ALL_STAGES: Stage[] = ['APPLIED', 'SHORTLISTED', 'TEST', 'INTERVIEW', 'OFFER', 'REJECTED']

export default function DriveApplicants() {
  const { id = '' } = useParams()
  const fetchDrive = useCallback(() => api.drive(getToken(), id), [id])
  const fetchApplicants = useCallback(() => api.applicants(getToken(), id), [id])
  const drive = useLoad(fetchDrive, 'drives')
  const applicants = useLoad(fetchApplicants, 'applicants')
  const [busy, setBusy] = useState<string | null>(null)
  const [actionError, setActionError] = useState('')
  const [filter, setFilter] = useState<Stage | 'ALL'>('ALL')
  const [confirming, setConfirming] = useState<string | null>(null)

  async function changeStage(app: Application, stage: Stage) {
    if (stage === app.stage) return
    setBusy(app.id)
    setActionError('')
    try {
      const updated = await api.setStage(getToken(), app.id, stage)
      applicants.setData((list) => list.map((a) => (a.id === app.id ? updated : a)))
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong. Try again.')
    } finally {
      setBusy(null)
    }
  }

  async function remove(app: Application) {
    setBusy(app.id)
    setActionError('')
    try {
      await api.removeApplication(getToken(), app.id)
      applicants.setData((rows) => rows.filter((a) => a.id !== app.id))
      drive.setData((d) => (d.applicantCount != null ? { ...d, applicantCount: Math.max(0, d.applicantCount - 1) } : d))
      setConfirming(null)
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong. Try again.')
    } finally {
      setBusy(null)
    }
  }

  const list = applicants.data ?? []
  const shown = filter === 'ALL' ? list : list.filter((a) => a.stage === filter)
  const counts = ALL_STAGES.map((s) => [s, list.filter((a) => a.stage === s).length] as const).filter(([, n]) => n > 0)

  return (
    <>
      <Link to="/drives" className="mb-4 inline-flex items-center gap-1.5 text-sm text-ink-soft hover:text-ink">
        <ArrowLeft className="size-4" aria-hidden="true" /> All drives
      </Link>

      {drive.error && !drive.data ? (
        <FormError>{drive.error}</FormError>
      ) : drive.data ? (
        <>
          <PageHeader title={drive.data.company} subtitle={drive.data.roleTitle} />
          <Panel className="mb-8 -mt-4">
            <div className="-mt-3">
              <DriveMeta drive={drive.data} />
              <Requirement drive={drive.data} />
            </div>
          </Panel>
        </>
      ) : null}

      <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
        <h2 className="font-display text-lg font-semibold">Applicants{applicants.data ? ` (${list.length})` : ''}</h2>
        {list.length > 0 && (
          <Select
            aria-label="Filter by stage"
            value={filter}
            onChange={(e) => setFilter(e.target.value as Stage | 'ALL')}
            className="w-48"
          >
            <option value="ALL">All stages</option>
            {counts.map(([s, n]) => (
              <option key={s} value={s}>
                {STAGE_LABEL[s]} ({n})
              </option>
            ))}
          </Select>
        )}
      </div>

      {actionError && (
        <div className="mb-4">
          <FormError>{actionError}</FormError>
        </div>
      )}

      {applicants.loading && !applicants.data ? (
        <p role="status" className="text-ink-soft">
          Loading applicants…
        </p>
      ) : applicants.error && !applicants.data ? (
        <div className="space-y-3">
          <FormError>{applicants.error}</FormError>
          <Button variant="secondary" onClick={applicants.reload}>
            Try again
          </Button>
        </div>
      ) : list.length === 0 ? (
        <Panel className="py-12 text-center">
          <p className="font-display text-lg font-semibold">No applicants yet</p>
          <p className="mt-1 text-sm text-ink-soft">Students who apply will appear here with their CGPA and batch.</p>
        </Panel>
      ) : (
        <Panel className="!p-0">
          <ul className="divide-y divide-line">
            {shown.map((a) => (
              <li key={a.id} className="flex flex-wrap items-center justify-between gap-4 p-4 md:px-6">
                <div className="min-w-0">
                  <p className="font-medium">{a.studentName}</p>
                  <p className="truncate text-sm text-ink-soft">{a.studentEmail}</p>
                  <p className="mt-1 text-sm text-ink-soft">
                    {[a.cgpa != null ? `CGPA ${a.cgpa.toFixed(2)}` : null, a.course, a.batch].filter(Boolean).join(' · ')}
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <Select
                    aria-label={`Move ${a.studentName} to stage`}
                    value={a.stage}
                    disabled={busy === a.id}
                    onChange={(e) => changeStage(a, e.target.value as Stage)}
                    className="w-40"
                  >
                    {ALL_STAGES.map((s) => (
                      <option key={s} value={s}>
                        {STAGE_LABEL[s]}
                      </option>
                    ))}
                  </Select>
                  {confirming === a.id ? (
                    <span className="flex items-center gap-2">
                      <Button variant="secondary" className="h-10 text-sm !text-bad" loading={busy === a.id} onClick={() => remove(a)}>
                        Remove application
                      </Button>
                      <Button variant="ghost" className="h-10 text-sm" onClick={() => setConfirming(null)}>
                        Keep
                      </Button>
                    </span>
                  ) : (
                    <button
                      type="button"
                      aria-label={`Remove ${a.studentName}'s application`}
                      title="Remove application so they can apply again"
                      onClick={() => setConfirming(a.id)}
                      className="inline-grid size-10 place-items-center rounded-lg text-ink-soft transition-colors hover:bg-sunken hover:text-bad"
                    >
                      <Trash2 className="size-[18px]" aria-hidden="true" />
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </Panel>
      )}
    </>
  )
}
