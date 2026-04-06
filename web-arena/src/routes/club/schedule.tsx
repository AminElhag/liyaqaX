import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/club/schedule')({
  component: ClubSchedulePage,
})

function ClubSchedulePage() {
  return <div className="p-4">Coming soon</div>
}
