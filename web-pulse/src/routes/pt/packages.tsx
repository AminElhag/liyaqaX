import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/pt/packages')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/pt/packages"!</div>
}
