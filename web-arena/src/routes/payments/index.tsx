import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/payments/')({
  component: PaymentsPage,
})

function PaymentsPage() {
  return <div className="p-4">Coming soon</div>
}
