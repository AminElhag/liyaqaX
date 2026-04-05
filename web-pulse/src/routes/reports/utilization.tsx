import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/reports/utilization')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/reports/utilization"!</div>
}
