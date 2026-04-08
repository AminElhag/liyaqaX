import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/messages/$threadId')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/messages/$threadId"!</div>
}
