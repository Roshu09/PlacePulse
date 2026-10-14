import { useState, type FormEvent } from 'react'
import { Check } from 'lucide-react'
import { ApiError } from '../lib/api'
import { api } from '../lib/api'
import { getToken, useAuth } from '../lib/auth'
import { BATCH_YEARS, passOutYear } from '../lib/batches'
import { Button, Field, FormError, Select } from './ui'

/** The details drive eligibility is checked against. Students fill this once. */
export function ProfileForm({ onSaved }: { onSaved?: () => void }) {
  const { user, updateUser } = useAuth()
  const [cgpa, setCgpa] = useState(user?.cgpa != null ? String(user.cgpa) : '')
  const [course, setCourse] = useState(user?.course ?? '')
  // older profiles may hold "2025-27"; show that as its pass-out year
  const [batch, setBatch] = useState(() => {
    const year = passOutYear(user?.batch)
    return year ? String(year) : ''
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setErrors({})
    setFormError('')
    setSaved(false)

    const trimmed = cgpa.trim()
    const value = trimmed === '' ? null : Number(trimmed)
    if (value !== null && (Number.isNaN(value) || value < 0 || value > 10)) {
      setErrors({ cgpa: 'CGPA must be a number between 0 and 10' })
      return
    }

    setSaving(true)
    try {
      const updated = await api.updateProfile(getToken(), {
        cgpa: value,
        course: course.trim() || null,
        batch: batch.trim() || null,
      })
      updateUser(updated)
      setSaved(true)
      onSaved?.()
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
    <form onSubmit={submit} noValidate className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-3">
        <Field
          label="CGPA"
          type="number"
          inputMode="decimal"
          step="0.01"
          min="0"
          max="10"
          value={cgpa}
          onChange={(e) => setCgpa(e.target.value)}
          error={errors.cgpa}
          placeholder="8.25"
        />
        <Field label="Course" value={course} onChange={(e) => setCourse(e.target.value)} error={errors.course} placeholder="MCA" />
        <div>
          <Select label="Pass-out year" value={batch} onChange={(e) => setBatch(e.target.value)}>
            <option value="">Choose…</option>
            {[...new Set([...BATCH_YEARS, ...(batch ? [Number(batch)] : [])])].sort().map((y) => (
              <option key={y} value={y}>
                {y}
              </option>
            ))}
          </Select>
          {errors.batch && <p className="mt-1.5 text-sm text-bad">{errors.batch}</p>}
        </div>
      </div>
      <FormError>{formError}</FormError>
      <div className="flex items-center gap-3">
        <Button type="submit" loading={saving}>
          Save details
        </Button>
        {saved && (
          <span role="status" className="inline-flex items-center gap-1.5 text-sm text-good">
            <Check className="size-4" aria-hidden="true" /> Saved
          </span>
        )}
      </div>
    </form>
  )
}
