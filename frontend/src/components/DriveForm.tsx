import { useRef, useState, type FormEvent } from 'react'
import { ImagePlus, X } from 'lucide-react'
import { ApiError, api, logoSrc, type Drive } from '../lib/api'
import { getToken } from '../lib/auth'
import { BATCH_YEARS, CGPA_OPTIONS, cgpaOptionLabel } from '../lib/batches'
import { Button, Field, FormError, Select, TextArea } from './ui'

const LOGO_MAX_BYTES = 500 * 1024

function today(): string {
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

interface Props {
  /** Pass a drive to edit it; leave out to post a new one. */
  drive?: Drive
  onSaved: (drive: Drive) => void
  onCancel: () => void
}

export function DriveForm({ drive, onSaved, onCancel }: Props) {
  const editing = drive !== undefined
  const [company, setCompany] = useState(drive?.company ?? '')
  const [roleTitle, setRoleTitle] = useState(drive?.roleTitle ?? '')
  const [packageLpa, setPackageLpa] = useState(drive?.packageLpa != null ? String(drive.packageLpa) : '')
  const [location, setLocation] = useState(drive?.location ?? '')
  const [minCgpa, setMinCgpa] = useState<number | ''>(drive ? drive.minCgpa : '')
  const [batches, setBatches] = useState<number[]>(drive?.eligibleBatches ?? [])
  const [deadline, setDeadline] = useState(drive?.deadline ?? '')
  const [description, setDescription] = useState(drive?.description ?? '')
  const [logo, setLogo] = useState<File | null>(null)
  const [logoPreview, setLogoPreview] = useState<string | null>(null)
  const [removeLogo, setRemoveLogo] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  // a drive saved earlier with a CGPA that is not on the list (like 7.25) still shows it
  const cgpaChoices = minCgpa !== '' && !CGPA_OPTIONS.includes(minCgpa) ? [...CGPA_OPTIONS, minCgpa].sort((a, b) => a - b) : CGPA_OPTIONS
  // a batch saved earlier that is no longer on the list stays selectable
  const batchChoices = [...new Set([...BATCH_YEARS, ...batches])].sort()
  const currentLogo = drive && !removeLogo ? logoSrc(drive) : null
  const shownLogo = logoPreview ?? currentLogo

  function toggleBatch(year: number) {
    setBatches((list) => (list.includes(year) ? list.filter((y) => y !== year) : [...list, year].sort()))
  }

  function pickLogo(file: File | undefined) {
    if (!file) return
    setErrors((e) => ({ ...e, logo: '' }))
    if (file.type !== 'image/png') {
      setErrors((e) => ({ ...e, logo: 'Choose a PNG image' }))
      return
    }
    if (file.size > LOGO_MAX_BYTES) {
      setErrors((e) => ({ ...e, logo: 'The PNG must be under 500 KB' }))
      return
    }
    if (logoPreview) URL.revokeObjectURL(logoPreview)
    setLogo(file)
    setLogoPreview(URL.createObjectURL(file))
    setRemoveLogo(false)
  }

  function clearLogo() {
    if (logoPreview) URL.revokeObjectURL(logoPreview)
    setLogo(null)
    setLogoPreview(null)
    if (drive?.logoUrl) setRemoveLogo(true)
    if (fileInput.current) fileInput.current.value = ''
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    const next: Record<string, string> = {}
    if (!company.trim()) next.company = 'Enter the company name'
    if (!roleTitle.trim()) next.roleTitle = 'Enter the role'
    if (minCgpa === '') next.minCgpa = 'Choose the minimum CGPA'
    if (!deadline) next.deadline = 'Pick the last date to apply'
    const lpa = packageLpa.trim() === '' ? null : Number(packageLpa)
    if (lpa !== null && (Number.isNaN(lpa) || lpa < 0)) next.packageLpa = 'Enter the package in LPA, like 6.5'
    setErrors(next)
    setFormError('')
    if (Object.keys(next).length > 0 || minCgpa === '') return

    const input = {
      company: company.trim(),
      roleTitle: roleTitle.trim(),
      description: description.trim() || null,
      location: location.trim() || null,
      packageLpa: lpa,
      minCgpa,
      eligibleBatches: batches,
      deadline,
    }

    setSaving(true)
    try {
      const saved = editing
        ? await api.updateDrive(getToken(), drive.id, { ...input, removeLogo }, logo)
        : await api.createDrive(getToken(), input, logo)
      onSaved(saved)
    } catch (err) {
      if (err instanceof ApiError) {
        setErrors(err.fieldErrors)
        if (Object.keys(err.fieldErrors).length === 0) setFormError(err.message)
      } else {
        setFormError('Something went wrong. Try again.')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Company" value={company} onChange={(e) => setCompany(e.target.value)} error={errors.company} placeholder="Acme Technologies" />
        <Field label="Role" value={roleTitle} onChange={(e) => setRoleTitle(e.target.value)} error={errors.roleTitle} placeholder="Software Engineer" />
        <Field
          label="Package (LPA)"
          type="number"
          inputMode="decimal"
          step="0.1"
          min="0"
          value={packageLpa}
          onChange={(e) => setPackageLpa(e.target.value)}
          error={errors.packageLpa}
          hint="Optional"
          placeholder="6.5"
        />
        <Field label="Location" value={location} onChange={(e) => setLocation(e.target.value)} error={errors.location} hint="Optional" placeholder="Bengaluru" />
        <div>
          <Select
            label="Minimum CGPA"
            value={minCgpa === '' ? '' : String(minCgpa)}
            onChange={(e) => setMinCgpa(e.target.value === '' ? '' : Number(e.target.value))}
            aria-invalid={errors.minCgpa ? true : undefined}
          >
            <option value="">Choose…</option>
            {cgpaChoices.map((c) => (
              <option key={c} value={c}>
                {cgpaOptionLabel(c)}
              </option>
            ))}
          </Select>
          {errors.minCgpa && <p className="mt-1.5 text-sm text-bad">{errors.minCgpa}</p>}
        </div>
        <Field
          label="Last date to apply"
          type="date"
          min={editing ? undefined : today()}
          value={deadline}
          onChange={(e) => setDeadline(e.target.value)}
          error={errors.deadline}
          hint="Applications close at the end of this day (IST)"
        />
      </div>

      <fieldset>
        <legend className="mb-1.5 text-sm font-medium">Eligible batches (pass-out year)</legend>
        <div className="flex flex-wrap gap-2">
          {batchChoices.map((year) => {
            const on = batches.includes(year)
            return (
              <label
                key={year}
                className={`inline-flex h-10 cursor-pointer select-none items-center rounded-lg border px-4 text-sm font-medium transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-[var(--focus)] ${
                  on ? 'border-transparent bg-signal text-on-signal' : 'border-line bg-surface text-ink hover:bg-sunken'
                }`}
              >
                <input type="checkbox" className="sr-only" checked={on} onChange={() => toggleBatch(year)} />
                {year}
              </label>
            )
          })}
        </div>
        <p className="mt-1.5 text-sm text-ink-soft">
          {batches.length === 0 ? 'None selected: students from every batch can apply.' : `Open to students passing out in ${batches.join(', ')}.`}
        </p>
        {errors.eligibleBatches && <p className="mt-1.5 text-sm text-bad">{errors.eligibleBatches}</p>}
      </fieldset>

      <div>
        <p className="mb-1.5 text-sm font-medium">Company logo</p>
        <div className="flex items-center gap-4">
          <span className="grid size-14 shrink-0 place-items-center overflow-hidden rounded-lg bg-sunken ring-1 ring-line">
            {shownLogo ? <img src={shownLogo} alt="" className="size-full object-contain" /> : <ImagePlus className="size-5 text-ink-soft" aria-hidden="true" />}
          </span>
          <div className="flex flex-wrap items-center gap-2">
            <Button type="button" variant="secondary" className="h-10 text-sm" onClick={() => fileInput.current?.click()}>
              {shownLogo ? 'Change PNG' : 'Upload PNG'}
            </Button>
            {shownLogo && (
              <Button type="button" variant="ghost" className="h-10 text-sm" onClick={clearLogo}>
                <X className="size-4" aria-hidden="true" /> Remove
              </Button>
            )}
          </div>
          <input
            ref={fileInput}
            type="file"
            accept="image/png"
            className="sr-only"
            aria-label="Company logo PNG"
            tabIndex={-1}
            onChange={(e) => pickLogo(e.target.files?.[0])}
          />
        </div>
        <p className="mt-1.5 text-sm text-ink-soft">Optional. A square PNG under 500 KB works best.</p>
        {errors.logo && (
          <p role="alert" className="mt-1.5 text-sm text-bad">
            {errors.logo}
          </p>
        )}
      </div>

      <TextArea
        label="Details"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        error={errors.description}
        hint="Optional. Selection rounds, bond, skills they look for"
      />

      {editing && (
        <p className="rounded-lg bg-sunken px-4 py-3 text-sm text-ink-soft">
          Students who already applied keep their applications. Students are notified of what changed, and the new rules apply to anyone who applies next.
        </p>
      )}

      <FormError>{formError}</FormError>
      <div className="flex flex-wrap gap-3">
        <Button type="submit" loading={saving}>
          {editing ? 'Save changes' : 'Post drive'}
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
