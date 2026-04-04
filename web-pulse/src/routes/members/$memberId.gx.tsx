import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/members/$memberId/gx')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/members/$memberId/gx"!</div>
}
