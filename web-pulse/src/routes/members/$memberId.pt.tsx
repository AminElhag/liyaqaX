import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/members/$memberId/pt')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/members/$memberId/pt"!</div>
}
