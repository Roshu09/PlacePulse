import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Download, Sparkles, Trash2 } from 'lucide-react'
import { DriveMeta, Requirement } from '../components/DriveCard'
import { PageHeader, Panel } from '../components/Page'
import { Badge, Button, FormError, Select } from '../components/ui'
import { ApiError, api, type Application, type RankedApplicant, type Stage } from '../lib/api'
import { getToken } from '../lib/auth'
import { STAGE_LABEL } from '../lib/format'
import { useLoad } from '../lib/useLoad'

function FitBadge({ row }: { row?: RankedApplicant }) {
  if (!row || row.score == null) {
    return <span className="ml-2 text-xs font-normal text-ink-soft">no resume</span>
  }
  return (
    <span className="ml-2">
      <Badge tone={row.score >= 70 ? 'good' : row.score >= 40 ? 'signal' : 'bad'}>
        {row.score}% fit{row.aiScore == null ? ' (text match)' : ''}
      </Badge>
    </span>
  )
}

const ALL_STAGES: Stage[] = ['APPLIED', 'SHORTLISTED', 'TEST', 'INTERVIEW', 'OFFER', 'REJECTED', 'DEBARRED']

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
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [bulkStageTo, setBulkStageTo] = useState<Stage>('SHORTLISTED')
  const [bulkBusy, setBulkBusy] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [fit, setFit] = useState<Map<string, RankedApplicant> | null>(null)
  const [ranking, setRanking] = useState(false)
  const [rankNote, setRankNote] = useState('')

  async function rankByFit() {
    setRanking(true)
    setActionError('')
    try {
      const rows = await api.rankApplicants(getToken(), id)
      setFit(new Map(rows.map((r) => [r.studentId, r])))
      const withResume = rows.filter((r) => r.hasResume).length
      setRankNote(
        withResume === 0
          ? `None of the ${rows.length} applicants has uploaded a resume yet, so there is nothing to rank.`
          : `${withResume} of ${rows.length} applicants have a resume. The rest are listed last.`,
      )
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong. Try again.')
    } finally {
      setRanking(false)
    }
  }

  function toggle(appId: string) {
    setSelected((s) => {
      const next = new Set(s)
      if (next.has(appId)) next.delete(appId)
      else next.add(appId)
      return next
    })
  }

  async function moveSelected(ids: string[]) {
    setBulkBusy(true)
    setActionError('')
    try {
      const updated = await api.bulkSetStage(getToken(), ids, bulkStageTo)
      const byId = new Map(updated.map((a) => [a.id, a]))
      applicants.setData((rows) => rows.map((a) => byId.get(a.id) ?? a))
      setSelected(new Set())
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong. Try again.')
    } finally {
      setBulkBusy(false)
    }
  }

  async function exportCsv() {
    setExporting(true)
    setActionError('')
    try {
      const name = `${drive.data?.company ?? 'drive'}-${drive.data?.roleTitle ?? ''}-applicants.csv`.replace(/[^A-Za-z0-9.]+/g, '-')
      await api.downloadApplicantsCsv(getToken(), id, name)
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong. Try again.')
    } finally {
      setExporting(false)
    }
  }

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
  const filtered = filter === 'ALL' ? list : list.filter((a) => a.stage === filter)
  // once ranked, best fit first; people without a resume go last
  const shown = fit
    ? [...filtered].sort((a, b) => (fit.get(b.studentId)?.score ?? -1) - (fit.get(a.studentId)?.score ?? -1))
    : filtered
  const selectedIds = list.filter((a) => selected.has(a.id)).map((a) => a.id)
  const allShownSelected = shown.length > 0 && shown.every((a) => selected.has(a.id))
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
          <div className="flex items-end gap-3">
            <Button variant="secondary" className="h-11" loading={ranking} onClick={rankByFit}>
              <Sparkles className="size-4" aria-hidden="true" /> {fit ? 'Refresh fit' : 'Rank by fit'}
            </Button>
            <Button variant="secondary" className="h-11" loading={exporting} onClick={exportCsv}>
              <Download className="size-4" aria-hidden="true" /> Export CSV
            </Button>
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
          </div>
        )}
      </div>

      {selectedIds.length > 0 && (
        <div role="region" aria-label="Bulk actions" className="mb-4 flex flex-wrap items-center gap-3 rounded-xl bg-sunken p-3 ring-1 ring-line">
          <span className="text-sm font-medium">{selectedIds.length} selected</span>
          <Select aria-label="Move selected to stage" value={bulkStageTo} onChange={(e) => setBulkStageTo(e.target.value as Stage)} className="w-44">
            {ALL_STAGES.filter((s) => s !== 'DEBARRED').map((s) => (
              <option key={s} value={s}>
                {STAGE_LABEL[s]}
              </option>
            ))}
          </Select>
          <Button className="h-10 text-sm" loading={bulkBusy} onClick={() => moveSelected(selectedIds)}>
            Move selected
          </Button>
          <Button variant="ghost" className="h-10 text-sm" onClick={() => setSelected(new Set())}>
            Clear
          </Button>
        </div>
      )}

      {actionError && (
        <div className="mb-4">
          <FormError>{actionError}</FormError>
        </div>
      )}

      {rankNote && (
        <p role="status" className="mb-4 rounded-lg bg-sunken px-4 py-3 text-sm text-ink-soft ring-1 ring-line">
          {rankNote}
        </p>
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
          <label className="flex items-center gap-3 border-b border-line px-4 py-2.5 text-sm text-ink-soft md:px-6">
            <input
              type="checkbox"
              checked={allShownSelected}
              onChange={() => setSelected(allShownSelected ? new Set() : new Set(shown.map((a) => a.id)))}
              className="size-4"
            />
            Select all{filter === 'ALL' ? '' : ` ${STAGE_LABEL[filter as Stage].toLowerCase()}`} ({shown.length})
          </label>
          <ul className="divide-y divide-line">
            {shown.map((a) => (
              <li key={a.id} className="flex flex-wrap items-center justify-between gap-4 p-4 md:px-6">
                <input
                  type="checkbox"
                  aria-label={`Select ${a.studentName}`}
                  checked={selected.has(a.id)}
                  onChange={() => toggle(a.id)}
                  className="size-4 shrink-0"
                />
                <div className="min-w-0 flex-1">
                  <p className="font-medium">
                    {a.studentName}
                    {fit && <FitBadge row={fit.get(a.studentId)} />}
                  </p>
                  <p className="truncate text-sm text-ink-soft">{a.studentEmail}</p>
                  <p className="mt-1 text-sm text-ink-soft">
                    {[a.cgpa != null ? `CGPA ${a.cgpa.toFixed(2)}` : null, a.course, a.batch].filter(Boolean).join(' · ')}
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  {a.stage === 'DEBARRED' && (
                    <Button
                      variant="secondary"
                      className="h-10 text-sm"
                      loading={busy === a.id}
                      onClick={() => changeStage(a, a.debarredFrom ?? 'APPLIED')}
                      title="Put this application back where it was before the student edited their profile"
                    >
                      Reinstate
                    </Button>
                  )}
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
