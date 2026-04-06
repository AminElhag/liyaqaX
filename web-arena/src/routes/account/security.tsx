import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/account/security')({
  component: AccountSecurityPage,
})

function AccountSecurityPage() {
  return <div className="p-4">Coming soon</div>
}
