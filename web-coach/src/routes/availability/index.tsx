import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/availability/')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/availability/"!</div>
}
