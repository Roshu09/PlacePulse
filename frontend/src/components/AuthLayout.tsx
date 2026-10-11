import type { ReactNode } from 'react'
import { PulseHero } from './PulseHero'
import { Wordmark } from './Logo'

interface AuthLayoutProps {
  title: string
  subtitle: string
  children: ReactNode
}

export function AuthLayout({ title, subtitle, children }: AuthLayoutProps) {
  return (
    <div className="grid min-h-dvh md:grid-cols-[1.15fr_1fr]">
      <aside className="hidden flex-col justify-between gap-12 bg-rail p-12 text-rail-ink md:flex">
        <Wordmark />
        <div>
          <p className="font-display text-5xl font-extrabold leading-[1.05] tracking-tight text-balance">
            Every drive, round by round.
          </p>
          <p className="mt-5 max-w-md text-lg text-rail-ink/75">
            Apply to company drives and follow each application from the first form to the offer.
          </p>
        </div>
        <PulseHero />
      </aside>

      <main className="flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <Wordmark badge className="mb-10 md:hidden" />
          <h1 className="font-display text-3xl font-bold tracking-tight">{title}</h1>
          <p className="mt-2 text-ink-soft">{subtitle}</p>
          <div className="mt-8">{children}</div>
        </div>
      </main>
    </div>
  )
}
