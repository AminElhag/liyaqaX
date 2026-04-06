import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/classes/my-classes')({
  component: ClassesMyClassesPage,
})

function ClassesMyClassesPage() {
  return <div className="p-4">Coming soon</div>
}
