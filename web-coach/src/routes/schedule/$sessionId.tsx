import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/schedule/$sessionId')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/schedule/$sessionId"!</div>
}
