import { PageHeader, Panel } from '../components/Page'
import { ProfileForm } from '../components/ProfileForm'
import { useAuth } from '../lib/auth'

export default function Profile() {
  const { user } = useAuth()
  if (!user) return null
  return (
    <>
      <PageHeader
        title="Profile"
        subtitle="Companies set a minimum CGPA and sometimes a batch. These details decide which drives you can apply to."
      />
      <Panel>
        <dl className="mb-6 grid gap-4 border-b border-line pb-6 sm:grid-cols-2">
          <div>
            <dt className="text-sm text-ink-soft">Name</dt>
            <dd className="font-medium">{user.fullName}</dd>
          </div>
          <div>
            <dt className="text-sm text-ink-soft">Email</dt>
            <dd className="break-all font-medium">{user.email}</dd>
          </div>
        </dl>
        <ProfileForm />
      </Panel>
    </>
  )
}
