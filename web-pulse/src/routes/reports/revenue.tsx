import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/reports/revenue')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/reports/revenue"!</div>
}
