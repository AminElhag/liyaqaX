import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/membership/upgrade')({
  component: MembershipUpgradePage,
})

function MembershipUpgradePage() {
  return <div className="p-4">Coming soon</div>
}
