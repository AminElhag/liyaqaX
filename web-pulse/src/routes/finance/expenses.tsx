import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/finance/expenses')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/finance/expenses"!</div>
}
