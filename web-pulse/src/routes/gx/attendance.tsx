import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/gx/attendance')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/gx/attendance"!</div>
}
