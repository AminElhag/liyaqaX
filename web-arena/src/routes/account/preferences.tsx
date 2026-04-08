import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/account/preferences')({
  component: AccountPreferencesPage,
})

function AccountPreferencesPage() {
  return <div className="p-4">Coming soon</div>
}
