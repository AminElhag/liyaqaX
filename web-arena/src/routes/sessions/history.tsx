import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/sessions/history')({
  component: SessionsHistoryPage,
})

function SessionsHistoryPage() {
  return <div className="p-4">Coming soon</div>
}
