import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/payments/invoices')({
  component: PaymentsInvoicesPage,
})

function PaymentsInvoicesPage() {
  return <div className="p-4">Coming soon</div>
}
