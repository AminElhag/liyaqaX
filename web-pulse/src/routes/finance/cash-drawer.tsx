import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/finance/cash-drawer')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/finance/cash-drawer"!</div>
}
