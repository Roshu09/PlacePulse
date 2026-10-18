import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import { Zap } from 'lucide-react'
import { PageHeader, Panel, Section } from '../components/Page'
import { api, type Analytics as AnalyticsData } from '../lib/api'
import { getToken } from '../lib/auth'
import { STAGE_LABEL } from '../lib/format'
import { useLoad } from '../lib/useLoad'
import type { Stage } from '../lib/api'

const FUNNEL_ORDER = ['APPLIED', 'SHORTLISTED', 'TEST', 'INTERVIEW', 'OFFER', 'REJECTED', 'DEBARRED']

/** Colour is never the only signal: every bar is also labelled with its number. */
const FUNNEL_BAR: Record<string, string> = {
  APPLIED: 'bg-ink-soft/50',
  SHORTLISTED: 'bg-signal',
  TEST: 'bg-signal',
  INTERVIEW: 'bg-signal',
  OFFER: 'bg-good',
  REJECTED: 'bg-bad/70',
  DEBARRED: 'bg-bad/40',
}

export default function Analytics() {
  const fetchIt = useCallback(() => api.analytics(getToken()), [])
  const { data, error, loading } = useLoad(fetchIt, 'analytics')

  return (
    <>
      <PageHeader title="Analytics" subtitle="Batch-wide numbers across every drive. Updates live as students apply and results change." />

      {loading && !data && <p className="text-ink-soft">Loading numbers…</p>}
      {error && !data && (
        <Panel>
          <p role="alert" className="text-bad">{error}</p>
        </Panel>
      )}

      {data && (
        <>
          <Tiles a={data.data} />
          <div className="mt-8 grid gap-6 lg:grid-cols-2">
            <Funnel a={data.data} />
            <Timeline a={data.data} />
          </div>
          <div className="mt-8">
            <Section title="Drive by drive">
              <Drives a={data.data} />
            </Section>
          </div>
          <p className="mt-6 flex items-center gap-1.5 text-xs text-ink-soft">
            <Zap className="size-3.5" aria-hidden="true" />
            {data.cache === 'HIT' ? 'Served from cache' : data.cache === 'MISS' ? 'Computed from the database' : 'Loaded'}
            {data.serverMs !== null && ` in ${data.serverMs} ms`}
          </p>
        </>
      )}
    </>
  )
}

function fmt(n: number | null, suffix = '') {
  return n === null ? '–' : `${n}${suffix}`
}

function Tiles({ a }: { a: AnalyticsData }) {
  const t = a.totals
  const tiles = [
    { label: 'Placement rate', value: `${t.placementRate}%`, note: `${t.studentsPlaced} of ${t.studentsApplied} students who applied` },
    { label: 'Offers', value: String(t.offers), note: `highest ${fmt(t.highestPackageLpa, ' LPA')}` },
    { label: 'Average package', value: fmt(t.averagePackageLpa, ' LPA'), note: 'weighted by offers' },
    { label: 'Applications', value: String(t.applications), note: `${t.openDrives} of ${t.drives} drives open` },
    { label: 'Average CGPA, placed', value: fmt(t.averageCgpaPlaced), note: `all applicants ${fmt(t.averageCgpaApplicants)}` },
  ]
  return (
    <dl className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
      {tiles.map((x) => (
        <div key={x.label} className="rounded-xl bg-surface p-4 ring-1 ring-line">
          <dt className="text-xs font-medium uppercase tracking-wide text-ink-soft">{x.label}</dt>
          <dd className="mt-1 font-display text-2xl font-bold tabular-nums md:text-3xl">{x.value}</dd>
          <dd className="mt-0.5 text-xs text-ink-soft">{x.note}</dd>
        </div>
      ))}
    </dl>
  )
}

function Funnel({ a }: { a: AnalyticsData }) {
  const max = Math.max(1, ...FUNNEL_ORDER.map((s) => a.funnel[s] ?? 0))
  return (
    <Panel>
      <h2 className="font-display text-lg font-semibold">Where applications are now</h2>
      <ul className="mt-4 space-y-2.5">
        {FUNNEL_ORDER.map((s) => {
          const n = a.funnel[s] ?? 0
          return (
            <li key={s} className="grid grid-cols-[6.5rem_1fr_2.5rem] items-center gap-3 text-sm">
              <span className="text-ink-soft">{STAGE_LABEL[s as Stage] ?? s}</span>
              <span className="h-3 rounded-full bg-sunken">
                <span className={`block h-3 rounded-full ${FUNNEL_BAR[s]}`} style={{ width: `${(n / max) * 100}%`, minWidth: n > 0 ? '0.5rem' : 0 }} />
              </span>
              <span className="text-right font-medium tabular-nums">{n}</span>
            </li>
          )
        })}
      </ul>
    </Panel>
  )
}

function Timeline({ a }: { a: AnalyticsData }) {
  const max = Math.max(1, ...a.timeline.map((d) => d.applications))
  const total = a.timeline.reduce((sum, d) => sum + d.applications, 0)
  return (
    <Panel>
      <h2 className="font-display text-lg font-semibold">Applications, last 14 days</h2>
      <p className="text-sm text-ink-soft">{total} in total</p>
      <div className="mt-4 flex h-32 items-end gap-1.5" role="img" aria-label={`Applications per day over the last 14 days, ${total} in total`}>
        {a.timeline.map((d) => (
          <div key={d.date} className="group relative flex h-full flex-1 items-end" title={`${d.date}: ${d.applications}`}>
            <span
              className="w-full rounded-t bg-signal"
              style={{ height: `${(d.applications / max) * 100}%`, minHeight: d.applications > 0 ? '0.25rem' : '1px', opacity: d.applications > 0 ? 1 : 0.35 }}
            />
          </div>
        ))}
      </div>
      <div className="mt-2 flex justify-between text-xs text-ink-soft">
        <span>{a.timeline[0]?.date.slice(5)}</span>
        <span>{a.timeline[a.timeline.length - 1]?.date.slice(5)}</span>
      </div>
    </Panel>
  )
}

function Drives({ a }: { a: AnalyticsData }) {
  if (a.drives.length === 0) {
    return (
      <Panel>
        <p className="text-ink-soft">No drives yet. Post one from the Drives page and the numbers will appear here.</p>
      </Panel>
    )
  }
  return (
    <Panel className="!p-0 overflow-x-auto">
      <table className="w-full min-w-[34rem] text-left text-sm">
        <thead className="text-xs uppercase tracking-wide text-ink-soft">
          <tr>
            <th className="px-4 py-3 font-medium">Drive</th>
            <th className="px-3 py-3 text-right font-medium">Applicants</th>
            <th className="px-3 py-3 text-right font-medium">Shortlisted</th>
            <th className="px-3 py-3 text-right font-medium">Offers</th>
            <th className="px-4 py-3 text-right font-medium">Conversion</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {a.drives.map((d) => (
            <tr key={d.driveId}>
              <td className="px-4 py-3">
                <Link to={`/drives/${d.driveId}`} className="font-medium hover:underline">{d.company}</Link>
                <span className="ml-2 text-ink-soft">{d.roleTitle}</span>
              </td>
              <td className="px-3 py-3 text-right tabular-nums">{d.applicants}</td>
              <td className="px-3 py-3 text-right tabular-nums">{d.shortlisted}</td>
              <td className="px-3 py-3 text-right font-medium tabular-nums">{d.offers}</td>
              <td className="px-4 py-3 text-right tabular-nums">{d.conversion}%</td>
            </tr>
          ))}
        </tbody>
      </table>
    </Panel>
  )
}
