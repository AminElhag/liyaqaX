import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/gx/$classId')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/gx/$classId"!</div>
}
