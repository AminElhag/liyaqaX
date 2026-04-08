import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/finance/payments')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/finance/payments"!</div>
}
