import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/finance/invoices')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/finance/invoices"!</div>
}
