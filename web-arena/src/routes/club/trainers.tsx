import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/club/trainers')({
  component: ClubTrainersPage,
})

function ClubTrainersPage() {
  return <div className="p-4">Coming soon</div>
}
