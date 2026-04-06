import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/classes/$classId')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/classes/$classId"!</div>
}
