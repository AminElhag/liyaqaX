import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/messages/')({
  component: MessagesPage,
})

function MessagesPage() {
  return <div className="p-4">Coming soon</div>
}
