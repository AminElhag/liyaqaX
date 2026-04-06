import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/classes/')({
  component: ClassesPage,
})

function ClassesPage() {
  return <div className="p-4">Coming soon</div>
}
