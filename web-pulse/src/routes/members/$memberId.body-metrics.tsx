import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/members/$memberId/body-metrics')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/members/$memberId/body-metrics"!</div>
}
