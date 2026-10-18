import { useCallback, useRef, useState } from 'react'
import { FileText, Trash2, Upload } from 'lucide-react'
import { Panel } from './Page'
import { Button, FormError } from './ui'
import { ApiError, api } from '../lib/api'
import { getToken } from '../lib/auth'
import { formatInstant } from '../lib/format'
import { useLoad } from '../lib/useLoad'

/** Upload the resume PDF the matcher reads. It is kept apart from the locked placement profile. */
export function ResumeCard() {
  const fetchInfo = useCallback(() => api.resumeInfo(getToken()), [])
  const { data, error, loading, setData } = useLoad(fetchInfo)
  const input = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')

  async function pick(file: File | undefined) {
    if (!file) return
    setMessage('')
    if (file.size > 2 * 1024 * 1024) return setMessage('That file is bigger than 2 MB. Export a smaller PDF.')
    setBusy(true)
    try {
      const info = await api.uploadResume(getToken(), file)
      setData(() => info)
    } catch (err) {
      setMessage(err instanceof ApiError ? err.message : 'Something went wrong. Try again.')
    } finally {
      setBusy(false)
      if (input.current) input.current.value = ''
    }
  }

  async function remove() {
    setBusy(true)
    setMessage('')
    try {
      await api.deleteResume(getToken())
      setData(() => ({ uploaded: false }))
    } catch (err) {
      setMessage(err instanceof ApiError ? err.message : 'Something went wrong. Try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Panel className="mb-5">
      <h2 className="font-display text-lg font-semibold">Resume for AI matching</h2>
      <p className="mb-4 mt-0.5 text-sm text-ink-soft">
        Upload a text PDF (up to 2 MB). On any drive you can then see how well it fits, what skills are missing and what to improve. Placement
        team members can see a fit ranking of applicants.
      </p>

      {loading && !data ? (
        <p role="status" className="text-sm text-ink-soft">
          Checking…
        </p>
      ) : error && !data ? (
        <FormError>Could not reach the AI service. Is it running?</FormError>
      ) : (
        <div className="flex flex-wrap items-center gap-3">
          {data?.uploaded && (
            <span className="inline-flex items-center gap-2 text-sm">
              <FileText className="size-4 text-ink-soft" aria-hidden="true" />
              <span className="font-medium">{data.filename}</span>
              <span className="text-ink-soft">uploaded {data.uploadedAt ? formatInstant(data.uploadedAt) : ''}</span>
            </span>
          )}
          <input
            ref={input}
            type="file"
            accept="application/pdf,.pdf"
            className="sr-only"
            aria-label="Choose resume PDF"
            onChange={(e) => pick(e.target.files?.[0])}
          />
          <Button variant="secondary" className="h-10 text-sm" loading={busy} onClick={() => input.current?.click()}>
            <Upload className="size-4" aria-hidden="true" /> {data?.uploaded ? 'Replace PDF' : 'Upload PDF'}
          </Button>
          {data?.uploaded && (
            <button
              type="button"
              onClick={remove}
              disabled={busy}
              aria-label="Delete uploaded resume"
              title="Delete uploaded resume"
              className="inline-grid size-10 place-items-center rounded-lg text-ink-soft hover:bg-sunken hover:text-bad"
            >
              <Trash2 className="size-[18px]" aria-hidden="true" />
            </button>
          )}
        </div>
      )}
      {message && (
        <div className="mt-3">
          <FormError>{message}</FormError>
        </div>
      )}
    </Panel>
  )
}
