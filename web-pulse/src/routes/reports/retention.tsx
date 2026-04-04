import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/reports/retention')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/reports/retention"!</div>
}
