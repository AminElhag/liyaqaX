import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/membership/')({
  component: MembershipPage,
})

function MembershipPage() {
  return <div className="p-4">Coming soon</div>
}
