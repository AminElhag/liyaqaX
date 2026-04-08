import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/club/')({
  component: ClubPage,
})

function ClubPage() {
  return <div className="p-4">Coming soon</div>
}
