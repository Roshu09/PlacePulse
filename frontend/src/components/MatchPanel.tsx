import { useState } from 'react'
import { ChevronDown, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Badge, Button, FormError } from './ui'
import { ApiError, api, type MatchResult } from '../lib/api'
import { getToken } from '../lib/auth'

function tone(score: number) {
  return score >= 70 ? 'bg-good' : score >= 40 ? 'bg-signal' : 'bg-bad'
}

/** "How well does my resume fit this drive?" One button, then the score, skills and suggestions. */
export function MatchPanel({ driveId }: { driveId: string }) {
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<MatchResult | null>(null)
  const [error, setError] = useState('')
  const [needsResume, setNeedsResume] = useState(false)

  async function run() {
    setOpen(true)
    setLoading(true)
    setError('')
    setNeedsResume(false)
    try {
      setResult(await api.matchDrive(getToken(), driveId))
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) setNeedsResume(true)
      else setError(err instanceof ApiError ? err.message : 'Something went wrong. Try again.')
    } finally {
      setLoading(false)
    }
  }

  if (!open) {
    return (
      <button
        type="button"
        onClick={run}
        className="mt-2 inline-flex items-center gap-1.5 text-sm font-medium text-ink-soft underline-offset-4 hover:text-ink hover:underline"
      >
        <Sparkles className="size-4" aria-hidden="true" /> Check how well my resume fits
      </button>
    )
  }

  return (
    <div className="mt-3 rounded-xl bg-sunken p-4 ring-1 ring-line" aria-live="polite">
      <div className="flex items-center justify-between gap-3">
        <p className="inline-flex items-center gap-1.5 font-display font-semibold">
          <Sparkles className="size-4" aria-hidden="true" /> Resume match
        </p>
        <button type="button" onClick={() => setOpen(false)} aria-label="Hide match" className="text-ink-soft hover:text-ink">
          <ChevronDown className="size-5 rotate-180" aria-hidden="true" />
        </button>
      </div>

      {loading && (
        <p role="status" className="mt-3 text-sm text-ink-soft">
          Reading your resume against this drive… this takes a few seconds.
        </p>
      )}

      {needsResume && (
        <p className="mt-3 text-sm">
          Upload your resume first.{' '}
          <Link to="/profile" className="font-medium underline underline-offset-4">
            Go to your profile
          </Link>
        </p>
      )}

      {error && (
        <div className="mt-3 space-y-2">
          <FormError>{error}</FormError>
          <Button variant="secondary" className="h-9 text-sm" onClick={run}>
            Try again
          </Button>
        </div>
      )}

      {result && !loading && (
        <div className="mt-3 space-y-4">
          <div>
            <div className="mb-1.5 flex items-baseline justify-between">
              <span className="text-2xl font-bold">{result.score}%</span>
              <span className="text-xs text-ink-soft">{result.aiGenerated ? 'AI analysis' : 'Text similarity only'}</span>
            </div>
            <div
              className="h-2 overflow-hidden rounded-full bg-surface"
              role="progressbar"
              aria-valuenow={result.score}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-label="Match score"
            >
              <div className={`h-full rounded-full ${tone(result.score)}`} style={{ width: `${result.score}%` }} />
            </div>
          </div>

          <p className="text-sm">{result.summary}</p>

          {result.matchedSkills.length > 0 && (
            <div>
              <p className="mb-1.5 text-sm font-medium">You already show</p>
              <div className="flex flex-wrap gap-1.5">
                {result.matchedSkills.map((s) => (
                  <Badge key={s} tone="good">
                    {s}
                  </Badge>
                ))}
              </div>
            </div>
          )}
          {result.missingSkills.length > 0 && (
            <div>
              <p className="mb-1.5 text-sm font-medium">Not visible on your resume</p>
              <div className="flex flex-wrap gap-1.5">
                {result.missingSkills.map((s) => (
                  <Badge key={s} tone="bad">
                    {s}
                  </Badge>
                ))}
              </div>
            </div>
          )}
          {result.suggestions.length > 0 && (
            <div>
              <p className="mb-1.5 text-sm font-medium">What to improve</p>
              <ul className="list-disc space-y-1 pl-5 text-sm text-ink-soft">
                {result.suggestions.map((s) => (
                  <li key={s}>{s}</li>
                ))}
              </ul>
            </div>
          )}
          {result.notice && <p className="text-xs text-ink-soft">{result.notice}</p>}
          <p className="text-xs text-ink-soft">A guide, not a verdict. Companies decide for themselves.</p>
        </div>
      )}
    </div>
  )
}
