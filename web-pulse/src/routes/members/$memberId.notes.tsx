import { createFileRoute } from '@tanstack/react-router'
import { NotesTimeline } from '@/components/members/NotesTimeline'

export const Route = createFileRoute('/members/$memberId/notes')({
  component: MemberNotesTab,
})

function MemberNotesTab() {
  const { memberId } = Route.useParams()
  return <NotesTimeline memberId={memberId} />
}
