import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/pt/')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/pt/"!</div>
}
