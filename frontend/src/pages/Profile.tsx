import { useState, type FormEvent, type ReactNode } from 'react'
import { Check, Lock } from 'lucide-react'
import { PageHeader, Panel } from '../components/Page'
import { ResumeCard } from '../components/ResumeCard'
import { Badge, Button, ConfirmDialog, Field, FormError, Select, TextArea } from '../components/ui'
import { ApiError, api, type ProfileInput, type User } from '../lib/api'
import { getToken, useAuth } from '../lib/auth'
import { BATCH_YEARS, passOutYear } from '../lib/batches'
import { formatInstant } from '../lib/format'

type Form = Record<string, string>

const REQUIRED: { key: string; label: string }[] = [
  { key: 'phone', label: 'Phone' },
  { key: 'course', label: 'Course' },
  { key: 'batch', label: 'Pass-out year' },
  { key: 'cgpa', label: 'CGPA' },
  { key: 'tenthPercent', label: '10th %' },
  { key: 'twelfthPercent', label: '12th %' },
  { key: 'resumeUrl', label: 'Resume link' },
]

const TEXT_KEYS = [
  'phone', 'course', 'resumeUrl', 'gender', 'city', 'graduationDegree', 'graduationUniversity',
  'githubUrl', 'linkedinUrl', 'portfolioUrl', 'skills', 'projects', 'certifications', 'dateOfBirth',
] as const
const NUMBER_KEYS = ['cgpa', 'tenthPercent', 'twelfthPercent', 'graduationPercent', 'activeBacklogs'] as const

function toForm(user: User): Form {
  const form: Form = {}
  for (const k of TEXT_KEYS) form[k] = (user[k] as string | null) ?? ''
  for (const k of NUMBER_KEYS) form[k] = user[k] == null ? '' : String(user[k])
  const year = passOutYear(user.batch)
  form.batch = year ? String(year) : ''
  return form
}

/** Form strings to the request. Returns field errors instead when a number is not a number. */
function toInput(form: Form): { input?: ProfileInput; errors?: Record<string, string> } {
  const errors: Record<string, string> = {}
  const num = (key: string, min: number, max: number) => {
    const raw = form[key].trim()
    if (raw === '') return null
    const n = Number(raw)
    if (Number.isNaN(n) || n < min || n > max) errors[key] = `Enter a number between ${min} and ${max}`
    return n
  }
  const text = (key: string) => form[key].trim() || null
  const input: ProfileInput = {
    cgpa: num('cgpa', 0, 10),
    course: text('course'),
    batch: text('batch'),
    phone: text('phone'),
    tenthPercent: num('tenthPercent', 0, 100),
    twelfthPercent: num('twelfthPercent', 0, 100),
    resumeUrl: text('resumeUrl'),
    dateOfBirth: text('dateOfBirth'),
    gender: text('gender'),
    city: text('city'),
    graduationDegree: text('graduationDegree'),
    graduationPercent: num('graduationPercent', 0, 100),
    graduationUniversity: text('graduationUniversity'),
    activeBacklogs: num('activeBacklogs', 0, 50),
    githubUrl: text('githubUrl'),
    linkedinUrl: text('linkedinUrl'),
    portfolioUrl: text('portfolioUrl'),
    skills: text('skills'),
    projects: text('projects'),
    certifications: text('certifications'),
  }
  return Object.keys(errors).length ? { errors } : { input }
}

function SectionCard({ title, hint, children }: { title: string; hint?: string; children: ReactNode }) {
  return (
    <Panel className="mb-5">
      <h2 className="font-display text-lg font-semibold">{title}</h2>
      {hint && <p className="mb-4 mt-0.5 text-sm text-ink-soft">{hint}</p>}
      <div className={`grid gap-4 sm:grid-cols-2 ${hint ? '' : 'mt-4'}`}>{children}</div>
    </Panel>
  )
}

export default function Profile() {
  const { user, updateUser } = useAuth()
  if (!user) return null
  if (user.role !== 'STUDENT') {
    return (
      <>
        <PageHeader title="Profile" />
        <Panel>
          <dl className="grid gap-4 sm:grid-cols-2">
            <div>
              <dt className="text-sm text-ink-soft">Name</dt>
              <dd className="font-medium">{user.fullName}</dd>
            </div>
            <div>
              <dt className="text-sm text-ink-soft">Email</dt>
              <dd className="break-all font-medium">{user.email}</dd>
            </div>
          </dl>
        </Panel>
      </>
    )
  }
  return <StudentProfile user={user} onUser={updateUser} />
}

function StudentProfile({ user, onUser }: { user: User; onUser: (u: User) => void }) {
  const [form, setForm] = useState<Form>(() => toForm(user))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [dialog, setDialog] = useState<'register' | 'edit' | null>(null)

  const registered = user.placementRegistered
  const set = (key: string) => (e: { target: { value: string } }) => {
    setForm((f) => ({ ...f, [key]: e.target.value }))
    setSaved(false)
  }
  const field = (key: string, label: string, extra: Record<string, unknown> = {}) => (
    <Field label={label} value={form[key]} onChange={set(key)} error={errors[key]} {...extra} />
  )

  const filled = REQUIRED.filter((r) => form[r.key].trim() !== '')
  const missing = REQUIRED.filter((r) => form[r.key].trim() === '')
  const percent = Math.round(((filled.length + 2) / (REQUIRED.length + 2)) * 100) // name and email always exist

  function fail(err: unknown) {
    if (err instanceof ApiError) {
      setErrors(err.fieldErrors)
      if (Object.keys(err.fieldErrors).length === 0 || err.status === 409) setFormError(err.message)
      else setFormError('Fix the highlighted fields and try again.')
    } else {
      setFormError('Something went wrong. Try again.')
    }
  }

  /** Validates, then saves. `acknowledge` is only sent for a registered profile after the student confirmed. */
  async function save(acknowledge: boolean): Promise<User | null> {
    setErrors({})
    setFormError('')
    const parsed = toInput(form)
    if (parsed.errors) {
      setErrors(parsed.errors)
      return null
    }
    if (registered && missing.length > 0) {
      setErrors(Object.fromEntries(missing.map((m) => [m.key, 'Required'])))
      setFormError('A registered profile must keep every required field filled.')
      return null
    }
    const updated = await api.updateProfile(getToken(), { ...parsed.input!, acknowledgeDebar: acknowledge })
    onUser(updated)
    return updated
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (registered) {
      // validate before warning, so the warning only appears for a save that can succeed
      const parsed = toInput(form)
      if (parsed.errors) return setErrors(parsed.errors)
      if (missing.length > 0) {
        setErrors(Object.fromEntries(missing.map((m) => [m.key, 'Required'])))
        return setFormError('A registered profile must keep every required field filled.')
      }
      setErrors({})
      setFormError('')
      return setDialog('edit')
    }
    setSaving(true)
    try {
      if (await save(false)) setSaved(true)
    } catch (err) {
      fail(err)
    } finally {
      setSaving(false)
    }
  }

  async function confirmEdit() {
    setSaving(true)
    try {
      if (await save(true)) setSaved(true)
      setDialog(null)
    } catch (err) {
      fail(err)
      setDialog(null)
    } finally {
      setSaving(false)
    }
  }

  async function startRegister() {
    if (missing.length > 0) {
      setErrors(Object.fromEntries(missing.map((m) => [m.key, 'Required'])))
      setFormError(`Fill in the required fields first: ${missing.map((m) => m.label).join(', ')}.`)
      return
    }
    setSaving(true)
    try {
      // save the draft so what is registered is exactly what is on screen
      if (await save(false)) setDialog('register')
    } catch (err) {
      fail(err)
    } finally {
      setSaving(false)
    }
  }

  async function confirmRegister() {
    setSaving(true)
    try {
      onUser(await api.registerForPlacements(getToken()))
      setDialog(null)
    } catch (err) {
      fail(err)
      setDialog(null)
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <PageHeader
        title="Profile"
        subtitle="Companies check your CGPA, batch and details when you apply. Fields marked required must be filled before you can register for placements."
      />

      <Panel className="mb-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="font-display text-lg font-semibold">{user.fullName}</p>
            <p className="break-all text-sm text-ink-soft">{user.email}</p>
          </div>
          {registered ? (
            <Badge tone="good">
              Registered{user.placementRegisteredAt ? ` on ${formatInstant(user.placementRegisteredAt)}` : ''}
            </Badge>
          ) : (
            <Badge tone="signal">Not registered</Badge>
          )}
        </div>
        <div className="mt-4">
          <div className="mb-1.5 flex justify-between text-sm">
            <span className="font-medium">Profile completeness</span>
            <span className="text-ink-soft">{percent}%</span>
          </div>
          <div className="h-2 overflow-hidden rounded-full bg-sunken" role="progressbar" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}>
            <div className="h-full rounded-full bg-good transition-all" style={{ width: `${percent}%` }} />
          </div>
          {missing.length > 0 && <p className="mt-2 text-sm text-ink-soft">Still needed: {missing.map((m) => m.label).join(', ')}.</p>}
        </div>
        {registered && (
          <p className="mt-4 flex items-start gap-2 rounded-lg bg-sunken px-3.5 py-2.5 text-sm text-ink-soft">
            <Lock className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <span>
              Your profile is locked in. If you edit it, you are debarred from drives you already applied to, until your
              placement team reinstates you. You can still apply to new drives.
            </span>
          </p>
        )}
      </Panel>

      <ResumeCard />

      <form onSubmit={onSubmit} noValidate>
        <SectionCard title="Personal" hint="Phone is required. The rest is optional.">
          {field('phone', 'Phone (required)', { type: 'tel', inputMode: 'tel', placeholder: '9876543210' })}
          {field('dateOfBirth', 'Date of birth', { type: 'date' })}
          <Select label="Gender" value={form.gender} onChange={set('gender')}>
            <option value="">Prefer not to say</option>
            <option value="Female">Female</option>
            <option value="Male">Male</option>
            <option value="Other">Other</option>
          </Select>
          {field('city', 'City')}
        </SectionCard>

        <SectionCard title="Academics" hint="CGPA, pass-out year, course, 10th and 12th are required.">
          {field('cgpa', 'CGPA (required)', { type: 'number', inputMode: 'decimal', step: '0.01', min: '0', max: '10', placeholder: '8.25' })}
          {field('course', 'Course (required)', { placeholder: 'MCA' })}
          <div>
            <Select label="Pass-out year (required)" value={form.batch} onChange={set('batch')}>
              <option value="">Choose…</option>
              {[...new Set([...BATCH_YEARS, ...(form.batch ? [Number(form.batch)] : [])])].sort().map((y) => (
                <option key={y} value={y}>
                  {y}
                </option>
              ))}
            </Select>
            {errors.batch && <p className="mt-1.5 text-sm text-bad">{errors.batch}</p>}
          </div>
          {field('activeBacklogs', 'Active backlogs', { type: 'number', inputMode: 'numeric', min: '0', placeholder: '0' })}
          {field('tenthPercent', '10th percentage (required)', { type: 'number', inputMode: 'decimal', step: '0.01', placeholder: '88.5' })}
          {field('twelfthPercent', '12th percentage (required)', { type: 'number', inputMode: 'decimal', step: '0.01', placeholder: '84' })}
          {field('graduationDegree', 'Graduation degree', { placeholder: 'BCA' })}
          {field('graduationPercent', 'Graduation percentage', { type: 'number', inputMode: 'decimal', step: '0.01' })}
          {field('graduationUniversity', 'Graduation university', { className: 'sm:col-span-2' })}
        </SectionCard>

        <SectionCard title="Links" hint="Resume link is required: a public Google Drive or similar link to your PDF.">
          {field('resumeUrl', 'Resume link (required)', { type: 'url', placeholder: 'https://drive.google.com/…', className: 'sm:col-span-2' })}
          {field('githubUrl', 'GitHub', { type: 'url', placeholder: 'https://github.com/you' })}
          {field('linkedinUrl', 'LinkedIn', { type: 'url', placeholder: 'https://linkedin.com/in/you' })}
          {field('portfolioUrl', 'Portfolio', { type: 'url', placeholder: 'https://…', className: 'sm:col-span-2' })}
        </SectionCard>

        <SectionCard title="Skills and experience" hint="Optional, but recruiters read these.">
          <TextArea label="Skills" className="sm:col-span-2" value={form.skills} onChange={set('skills')} error={errors.skills} placeholder="Java, Spring Boot, React, Kafka, Docker" />
          <TextArea label="Projects" className="sm:col-span-2" value={form.projects} onChange={set('projects')} error={errors.projects} placeholder="One per line: name, what it does, tech used" />
          <TextArea label="Certifications" className="sm:col-span-2" value={form.certifications} onChange={set('certifications')} error={errors.certifications} />
        </SectionCard>

        <FormError>{formError}</FormError>
        <div className="mt-4 flex flex-wrap items-center gap-3">
          <Button type="submit" variant={registered ? 'primary' : 'secondary'} loading={saving && dialog === null}>
            {registered ? 'Save changes' : 'Save draft'}
          </Button>
          {!registered && (
            <Button type="button" onClick={startRegister} loading={saving && dialog === null} disabled={saving}>
              Register for placements
            </Button>
          )}
          {saved && (
            <span role="status" className="inline-flex items-center gap-1.5 text-sm text-good">
              <Check className="size-4" aria-hidden="true" /> Saved
            </span>
          )}
        </div>
      </form>

      {dialog === 'register' && (
        <ConfirmDialog
          title="Register for placements?"
          confirmLabel="Yes, register me"
          loading={saving}
          onConfirm={confirmRegister}
          onCancel={() => setDialog(null)}
        >
          <p>After you register you can apply to every drive you are eligible for.</p>
          <p className="font-semibold text-ink">You will not be able to edit your profile freely afterwards.</p>
          <p>
            If you do edit it, you are debarred from the drives you already applied to (your placement team can reinstate you). You can
            still apply to new drives.
          </p>
        </ConfirmDialog>
      )}
      {dialog === 'edit' && (
        <ConfirmDialog
          title="Edit your registered profile?"
          confirmLabel="Save and accept"
          loading={saving}
          onConfirm={confirmEdit}
          onCancel={() => setDialog(null)}
        >
          <p className="font-semibold text-ink">Saving these changes debars you from every drive you already applied to.</p>
          <p>Applications that are still in progress are put on hold until your placement team reinstates them. Offers and rejections stay as they are. You can still apply to new drives.</p>
        </ConfirmDialog>
      )}
    </>
  )
}
