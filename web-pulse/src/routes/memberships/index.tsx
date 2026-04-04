import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/memberships/')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/memberships/"!</div>
}
