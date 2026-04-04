import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/pt/trainers')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/pt/trainers"!</div>
}
