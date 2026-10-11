import { Hammer } from 'lucide-react'
import { PageHeader, Panel } from '../components/Page'

export default function ComingSoon({ title, description }: { title: string; description: string }) {
  return (
    <>
      <PageHeader title={title} />
      <Panel className="flex flex-col items-center py-14 text-center">
        <span className="mb-4 grid size-12 place-items-center rounded-full bg-sunken text-ink-soft">
          <Hammer className="size-5" aria-hidden="true" />
        </span>
        <p className="font-display text-lg font-semibold">Not built yet</p>
        <p className="mt-1 max-w-sm text-sm text-ink-soft">{description}</p>
      </Panel>
    </>
  )
}
