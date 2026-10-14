import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { Briefcase, Check, Pencil, Plus } from 'lucide-react'
import { DriveCard } from '../components/DriveCard'
import { PageHeader, Panel } from '../components/Page'
import { DriveForm } from '../components/DriveForm'
import { ProfileForm } from '../components/ProfileForm'
import { Button, FormError } from '../components/ui'
import { ApiError, api, type Drive, type DriveStatus } from '../lib/api'
import { getToken, useAuth } from '../lib/auth'
import { useLoad } from '../lib/useLoad'

export default function Drives() {
  const { user } = useAuth()
  if (!user) return null
  return user.role === 'TPR' ? <TprDrives /> : <StudentDrives />
}

function EmptyState({ title, body }: { title: string; body: string }) {
  return (
    <Panel className="flex flex-col items-center py-12 text-center">
      <span className="mb-4 grid size-12 place-items-center rounded-full bg-sunken text-ink-soft">
        <Briefcase className="size-5" aria-hidden="true" />
      </span>
      <p className="font-display text-lg font-semibold">{title}</p>
      <p className="mt-1 max-w-sm text-sm text-ink-soft">{body}</p>
    </Panel>
  )
}

function LoadError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="space-y-3">
      <FormError>{message}</FormError>
      <Button variant="secondary" onClick={onRetry}>
        Try again
      </Button>
    </div>
  )
}

/* ------------------------------- Student ------------------------------- */

function StudentDrives() {
  const { user } = useAuth()
  const fetchDrives = useCallback(() => api.drives(getToken()), [])
  const { data, error, loading, reload, setData } = useLoad(fetchDrives, 'drives')
  const [applying, setApplying] = useState<string | null>(null)
  const [applyErrors, setApplyErrors] = useState<Record<string, string>>({})

  const needsProfile = user?.cgpa == null

  async function apply(drive: Drive) {
    setApplying(drive.id)
    setApplyErrors((e) => ({ ...e, [drive.id]: '' }))
    try {
      await api.apply(getToken(), drive.id)
      setData((list) => list.map((d) => (d.id === drive.id ? { ...d, applied: true } : d)))
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Something went wrong. Try again.'
      setApplyErrors((e) => ({ ...e, [drive.id]: message }))
      // already applied (409 from another tab/device): reflect it
      if (err instanceof ApiError && err.status === 409) reload()
    } finally {
      setApplying(null)
    }
  }

  return (
    <>
      <PageHeader title="Drives" subtitle="Company drives open to apply. We check your CGPA and batch for you when you apply." />

      {needsProfile && (
        <Panel className="mb-6 border-l-4 !border-l-signal">
          <p className="font-display text-lg font-semibold">Add your CGPA to apply</p>
          <p className="mb-4 mt-1 text-sm text-ink-soft">Drives have a minimum CGPA. You only need to enter this once.</p>
          <ProfileForm />
        </Panel>
      )}

      {loading && !data ? (
        <p role="status" className="text-ink-soft">
          Loading drives…
        </p>
      ) : error && !data ? (
        <LoadError message={error} onRetry={reload} />
      ) : data && data.length === 0 ? (
        <EmptyState title="No open drives right now" body="When your placement team posts a drive, it shows up here with its eligibility and last date." />
      ) : (
        <ul className="space-y-4">
          {data?.map((drive) => {
            const cgpa = user?.cgpa
            const below = cgpa != null && cgpa < drive.minCgpa
            return (
              <li key={drive.id}>
                <DriveCard
                  drive={drive}
                  actions={
                    drive.applied ? (
                      <span className="inline-flex items-center gap-3">
                        <span className="inline-flex items-center gap-1.5 text-sm font-semibold text-good">
                          <Check className="size-4" aria-hidden="true" /> You applied
                        </span>
                        <Link to="/applications" className="text-sm font-medium underline underline-offset-4">
                          Track it
                        </Link>
                      </span>
                    ) : (
                      <div className="w-full sm:w-auto">
                        <Button
                          onClick={() => apply(drive)}
                          loading={applying === drive.id}
                          disabled={needsProfile || !drive.acceptingApplications}
                          className="h-10 w-full sm:w-auto"
                        >
                          Apply
                        </Button>
                        {below && !applyErrors[drive.id] && (
                          <p className="mt-2 text-sm text-ink-soft">
                            Your CGPA is {cgpa?.toFixed(2)}, below the {drive.minCgpa.toFixed(2)} required.
                          </p>
                        )}
                        {applyErrors[drive.id] && (
                          <p role="alert" className="mt-2 text-sm text-bad">
                            {applyErrors[drive.id]}
                          </p>
                        )}
                      </div>
                    )
                  }
                />
              </li>
            )
          })}
        </ul>
      )}
    </>
  )
}

/* --------------------------------- TPR --------------------------------- */

function TprDrives() {
  const fetchDrives = useCallback(() => api.drives(getToken()), [])
  const { data, error, loading, reload, setData } = useLoad(fetchDrives, 'drives')
  const [posting, setPosting] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [actionError, setActionError] = useState('')

  async function toggle(drive: Drive) {
    const next: DriveStatus = drive.status === 'OPEN' ? 'CLOSED' : 'OPEN'
    setBusy(drive.id)
    setActionError('')
    try {
      const updated = await api.setDriveStatus(getToken(), drive.id, next)
      setData((list) => list.map((d) => (d.id === drive.id ? updated : d)))
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : 'Something went wrong. Try again.')
    } finally {
      setBusy(null)
    }
  }

  return (
    <>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <PageHeader title="Drives" subtitle="Post company drives and follow every applicant." />
        {!posting && (
          <Button onClick={() => setPosting(true)} className="mt-2">
            <Plus className="size-4" aria-hidden="true" /> Post a drive
          </Button>
        )}
      </div>

      {posting && (
        <Panel className="mb-6">
          <h2 className="mb-4 font-display text-lg font-semibold">New drive</h2>
          <DriveForm
            onCancel={() => setPosting(false)}
            onSaved={(drive) => {
              setData((list) => [drive, ...list])
              setPosting(false)
            }}
          />
        </Panel>
      )}

      {actionError && (
        <div className="mb-4">
          <FormError>{actionError}</FormError>
        </div>
      )}

      {loading && !data ? (
        <p role="status" className="text-ink-soft">
          Loading drives…
        </p>
      ) : error && !data ? (
        <LoadError message={error} onRetry={reload} />
      ) : data && data.length === 0 ? (
        !posting && <EmptyState title="No drives posted yet" body="Post the first drive and students in your batch can apply to it straight away." />
      ) : (
        <ul className="space-y-4">
          {data?.map((drive) => (
            <li key={drive.id}>
              {editingId === drive.id ? (
                <Panel>
                  <h2 className="mb-4 font-display text-lg font-semibold">Edit {drive.company}</h2>
                  <DriveForm
                    drive={drive}
                    onCancel={() => setEditingId(null)}
                    onSaved={(updated) => {
                      setData((list) => list.map((d) => (d.id === updated.id ? updated : d)))
                      setEditingId(null)
                    }}
                  />
                </Panel>
              ) : (
                <DriveCard
                  drive={drive}
                  linkToApplicants
                  actions={
                    <>
                      <Button variant="secondary" onClick={() => setEditingId(drive.id)} className="h-10 text-sm">
                        <Pencil className="size-4" aria-hidden="true" /> Edit
                      </Button>
                      <Button variant="secondary" onClick={() => toggle(drive)} loading={busy === drive.id} className="h-10 text-sm">
                        {drive.status === 'OPEN' ? 'Close applications' : 'Reopen'}
                      </Button>
                    </>
                  }
                />
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  )
}
