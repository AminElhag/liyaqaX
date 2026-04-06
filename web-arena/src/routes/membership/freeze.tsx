import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/membership/freeze')({
  component: MembershipFreezePage,
})

function MembershipFreezePage() {
  return <div className="p-4">Coming soon</div>
}
