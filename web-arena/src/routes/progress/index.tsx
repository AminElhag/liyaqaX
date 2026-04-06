import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/progress/')({
  component: ProgressPage,
})

function ProgressPage() {
  return <div className="p-4">Coming soon</div>
}
