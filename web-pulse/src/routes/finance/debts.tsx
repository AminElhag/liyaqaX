import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/finance/debts')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/finance/debts"!</div>
}
