import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/gx/')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/gx/"!</div>
}
