import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AuthLayout } from '../components/AuthLayout'
import { Button, Field, FormError } from '../components/ui'
import { ApiError } from '../lib/api'
import { useAuth } from '../lib/auth'

export default function Register() {
  const { register } = useAuth()
  const navigate = useNavigate()

  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [inviteCode, setInviteCode] = useState('')
  const [hasInvite, setHasInvite] = useState(false)

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError('')
    setFieldErrors({})
    setSubmitting(true)
    try {
      await register({
        fullName: fullName.trim(),
        email: email.trim(),
        password,
        inviteCode: hasInvite ? inviteCode : undefined,
      })
      navigate('/', { replace: true })
    } catch (err) {
      if (!(err instanceof ApiError)) {
        setError('Something went wrong. Try again.')
      } else if (err.status === 400) {
        setFieldErrors(err.fieldErrors)
      } else if (err.status === 409) {
        setFieldErrors({ email: 'This email is already registered. Sign in instead.' })
      } else if (err.status === 403) {
        setFieldErrors({ inviteCode: 'That invite code is not valid.' })
      } else {
        setError(err.message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout title="Create your account" subtitle="Students and representatives start here.">
      <form onSubmit={onSubmit} className="space-y-5" noValidate>
        <FormError>{error}</FormError>
        <Field
          label="Full name"
          autoComplete="name"
          value={fullName}
          onChange={(e) => setFullName(e.target.value)}
          error={fieldErrors.fullName}
          required
        />
        <Field
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldErrors.email}
          required
        />
        <Field
          label="Password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
          hint="At least 8 characters."
          required
        />

        {hasInvite ? (
          <Field
            label="TPR invite code"
            value={inviteCode}
            onChange={(e) => setInviteCode(e.target.value)}
            error={fieldErrors.inviteCode}
            hint="Only placement representatives have one. Students leave this out."
            autoComplete="off"
          />
        ) : (
          <button
            type="button"
            onClick={() => setHasInvite(true)}
            className="text-sm font-medium text-ink-soft underline underline-offset-4 hover:text-ink"
          >
            I have a TPR invite code
          </button>
        )}

        <Button type="submit" loading={submitting} className="w-full">
          Create account
        </Button>
      </form>
      <p className="mt-6 text-sm text-ink-soft">
        Already registered?{' '}
        <Link to="/login" className="font-semibold text-ink underline underline-offset-4">
          Sign in
        </Link>
      </p>
    </AuthLayout>
  )
}
