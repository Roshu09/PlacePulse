export const STAGES = ['Applied', 'Shortlisted', 'Test', 'Interview', 'Offer'] as const

/**
 * The stages every application moves through. `reached` is how many stages are done
 * (0 = none yet). With no applications it renders as an idle track.
 */
export function StageTrack({ reached = 0 }: { reached?: number }) {
  return (
    <ol className="flex items-start" aria-label="Application stages">
      {STAGES.map((stage, i) => {
        const done = i < reached
        const isLast = i === STAGES.length - 1
        return (
          <li key={stage} className="relative flex-1 text-center">
            {!isLast && (
              <span
                aria-hidden="true"
                className={`absolute left-1/2 top-[7px] h-0.5 w-full ${i < reached - 1 ? 'bg-good' : 'bg-ink-soft/25'}`}
              />
            )}
            <span
              aria-hidden="true"
              className={`relative mx-auto block size-4 rounded-full border-2 ${
                done ? 'border-good bg-good' : 'border-ink-soft/45 bg-surface'
              }`}
            />
            <span className={`mt-2 block text-xs md:text-[13px] ${done ? 'font-semibold' : 'text-ink-soft'}`}>{stage}</span>
          </li>
        )
      })}
    </ol>
  )
}
