import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/members/$memberId/payments')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/members/$memberId/payments"!</div>
}
