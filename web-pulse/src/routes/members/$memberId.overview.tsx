import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getMember, memberKeys } from '@/api/members'
import { MemberProfileCard } from '@/components/members/MemberProfileCard'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'

export const Route = createFileRoute('/members/$memberId/overview')({
  component: MemberOverviewTab,
})

function MemberOverviewTab() {
  const { memberId } = Route.useParams()
  const { t } = useTranslation()

  const { data: member, isLoading } = useQuery({
    queryKey: memberKeys.detail(memberId),
    queryFn: () => getMember(memberId),
    staleTime: 2 * 60 * 1000,
  })

  if (isLoading) {
    return <LoadingSkeleton rows={6} />
  }

  if (!member) {
    return (
      <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
        {t('members.profile.notFound')}
      </div>
    )
  }

  return <MemberProfileCard member={member} />
}
