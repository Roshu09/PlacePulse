export function LogoMark({ className = 'size-7' }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={className} aria-hidden="true">
      <path
        d="M2 17h6l3.5-8 5 15 3.5-7h10"
        fill="none"
        stroke="var(--signal)"
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/** `badge` puts the mark on a green tile so it stays legible on light backgrounds. */
export function Wordmark({ className = '', badge = false }: { className?: string; badge?: boolean }) {
  return (
    <span className={`inline-flex items-center gap-2 ${className}`}>
      {badge ? (
        <span className="grid size-8 place-items-center rounded-lg bg-rail">
          <LogoMark className="size-6" />
        </span>
      ) : (
        <LogoMark />
      )}
      <span className="font-display text-xl font-bold tracking-tight">PlacePulse</span>
    </span>
  )
}
