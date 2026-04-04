import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/members/new')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/members/new"!</div>
}
