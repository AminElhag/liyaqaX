import { createFileRoute } from '@tanstack/react-router'
import { MemberNotes } from '@/components/members/MemberNotes'

export const Route = createFileRoute('/members/$memberId')({
  component: MemberDetail,
})

function MemberDetail() {
  const { memberId } = Route.useParams()
  return (
    <div className="p-4">
      <MemberNotes memberId={memberId} />
    </div>
  )
}
