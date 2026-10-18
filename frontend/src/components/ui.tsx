import { forwardRef, useId, useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import { Eye, EyeOff, TriangleAlert } from 'lucide-react'

type Variant = 'primary' | 'secondary' | 'ghost'

const variants: Record<Variant, string> = {
  primary: 'bg-signal text-on-signal hover:bg-signal-strong font-semibold',
  secondary: 'bg-surface text-ink border border-line hover:bg-sunken font-medium',
  ghost: 'text-ink-soft hover:bg-sunken hover:text-ink font-medium',
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  loading?: boolean
}

export function Button({ variant = 'primary', loading = false, className = '', children, disabled, ...rest }: ButtonProps) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={`inline-flex h-11 items-center justify-center gap-2 rounded-lg px-4 text-[15px] transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${variants[variant]} ${className}`}
    >
      {loading && (
        <span
          aria-hidden="true"
          className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  )
}

export function IconButton({ label, className = '', children, ...rest }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return (
    <button
      {...rest}
      aria-label={label}
      title={label}
      className={`inline-grid size-10 place-items-center rounded-lg text-ink-soft transition-colors hover:bg-sunken hover:text-ink ${className}`}
    >
      {children}
    </button>
  )
}

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  hint?: string
}

export const Field = forwardRef<HTMLInputElement, FieldProps>(function Field(
  { label, error, hint, type = 'text', className = '', ...rest },
  ref,
) {
  const id = useId()
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined
  const isPassword = type === 'password'
  const [shown, setShown] = useState(false)

  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium">
        {label}
      </label>
      <div className="relative">
        <input
          {...rest}
          ref={ref}
          id={id}
          type={isPassword && shown ? 'text' : type}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={`h-11 w-full rounded-lg border bg-surface px-3.5 text-[15px] placeholder:text-ink-soft/50 ${
            error ? 'border-bad' : 'border-line'
          } ${isPassword ? 'pr-11' : ''}`}
        />
        {isPassword && (
          <button
            type="button"
            onClick={() => setShown((s) => !s)}
            aria-label={shown ? 'Hide password' : 'Show password'}
            className="absolute inset-y-0 right-0 grid w-11 place-items-center rounded-r-lg text-ink-soft hover:text-ink"
          >
            {shown ? <EyeOff className="size-[18px]" /> : <Eye className="size-[18px]" />}
          </button>
        )}
      </div>
      {error ? (
        <p id={`${id}-error`} className="mt-1.5 text-sm text-bad">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="mt-1.5 text-sm text-ink-soft">
          {hint}
        </p>
      ) : null}
    </div>
  )
})

export function FormError({ children }: { children: ReactNode }) {
  if (!children) return null
  return (
    <p role="alert" className="rounded-lg border border-bad/40 bg-bad/10 px-3.5 py-2.5 text-sm text-bad">
      {children}
    </p>
  )
}

const controlClass = (error?: string) =>
  `w-full rounded-lg border bg-surface px-3.5 text-[15px] placeholder:text-ink-soft/50 ${error ? 'border-bad' : 'border-line'}`

export const TextArea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; error?: string; hint?: string }>(
  function TextArea({ label, error, hint, className = '', ...rest }, ref) {
    const id = useId()
    return (
      <div className={className}>
        <label htmlFor={id} className="mb-1.5 block text-sm font-medium">
          {label}
        </label>
        <textarea {...rest} ref={ref} id={id} aria-invalid={error ? true : undefined} className={`${controlClass(error)} min-h-24 py-2.5`} />
        {error ? <p className="mt-1.5 text-sm text-bad">{error}</p> : hint ? <p className="mt-1.5 text-sm text-ink-soft">{hint}</p> : null}
      </div>
    )
  },
)

export function Select({
  label,
  className = '',
  children,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement> & { label?: string }) {
  const id = useId()
  return (
    <div className={className}>
      {label && (
        <label htmlFor={id} className="mb-1.5 block text-sm font-medium">
          {label}
        </label>
      )}
      <select {...rest} id={id} className={`${controlClass()} h-11`}>
        {children}
      </select>
    </div>
  )
}

type Tone = 'neutral' | 'good' | 'bad' | 'signal'

const tones: Record<Tone, string> = {
  neutral: 'bg-sunken text-ink-soft',
  good: 'bg-good/15 text-good',
  bad: 'bg-bad/12 text-bad',
  signal: 'bg-signal/25 text-ink',
}

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ${tones[tone]}`}>{children}</span>
  )
}

/** A blocking "are you sure" box. Escape or clicking outside cancels. */
export function ConfirmDialog({
  title,
  confirmLabel,
  cancelLabel = 'Cancel',
  loading = false,
  onConfirm,
  onCancel,
  children,
}: {
  title: string
  confirmLabel: string
  cancelLabel?: string
  loading?: boolean
  onConfirm: () => void
  onCancel: () => void
  children: ReactNode
}) {
  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-black/50 p-4"
      onClick={onCancel}
      onKeyDown={(e) => e.key === 'Escape' && onCancel()}
    >
      <div
        role="alertdialog"
        aria-modal="true"
        aria-label={title}
        className="w-full max-w-md rounded-xl bg-surface p-6 shadow-xl ring-1 ring-line"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-3 flex items-center gap-3">
          <span className="grid size-10 shrink-0 place-items-center rounded-full bg-signal/25">
            <TriangleAlert className="size-5" aria-hidden="true" />
          </span>
          <h2 className="font-display text-xl font-semibold">{title}</h2>
        </div>
        <div className="space-y-2 text-sm text-ink-soft">{children}</div>
        <div className="mt-6 flex justify-end gap-3">
          <Button variant="secondary" onClick={onCancel} disabled={loading} autoFocus>
            {cancelLabel}
          </Button>
          <Button onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}
