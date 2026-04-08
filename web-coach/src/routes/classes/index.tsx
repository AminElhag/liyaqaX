import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/classes/')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/classes/"!</div>
}
