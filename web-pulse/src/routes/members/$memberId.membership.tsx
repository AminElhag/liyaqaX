import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/members/$memberId/membership')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/members/$memberId/membership"!</div>
}
