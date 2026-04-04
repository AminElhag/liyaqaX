import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/memberships/plans')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/memberships/plans"!</div>
}
