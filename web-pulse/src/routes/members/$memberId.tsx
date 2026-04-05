import { createFileRoute, Link, Outlet } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getMember, memberKeys } from '@/api/members'
import { PageShell } from '@/components/layout/PageShell'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { MemberStatusBadge } from '@/components/members/MemberStatusBadge'

export const Route = createFileRoute('/members/$memberId')({
  component: MemberProfileLayout,
})

const tabs = [
  { key: 'overview', to: '/members/$memberId/overview' as const },
  { key: 'membership', to: '/members/$memberId/membership' as const },
  { key: 'payments', to: '/members/$memberId/payments' as const },
  { key: 'pt', to: '/members/$memberId/pt' as const },
  { key: 'gx', to: '/members/$memberId/gx' as const },
] as const

function MemberProfileLayout() {
  const { memberId } = Route.useParams()
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'

  const { data: member, isLoading, isError } = useQuery({
    queryKey: memberKeys.detail(memberId),
    queryFn: () => getMember(memberId),
    staleTime: 2 * 60 * 1000,
  })

  if (isLoading) {
    return (
      <PageShell title={t('members.profile.title')}>
        <LoadingSkeleton rows={6} />
      </PageShell>
    )
  }

  if (isError || !member) {
    return (
      <PageShell title={t('members.profile.title')}>
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
          {t('members.profile.notFound')}
        </div>
      </PageShell>
    )
  }

  const fullName = isAr
    ? `${member.firstNameAr} ${member.lastNameAr}`
    : `${member.firstNameEn} ${member.lastNameEn}`

  return (
    <PageShell
      title={fullName}
      actions={
        <div className="flex items-center gap-3">
          <MemberStatusBadge status={member.membershipStatus} />
          <Link
            to="/members"
            className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
          >
            {t('common.goBack')}
          </Link>
        </div>
      }
    >
      {/* Tab navigation */}
      <div className="mb-6 border-b border-gray-200">
        <nav className="-mb-px flex gap-6">
          {tabs.map((tab) => (
            <Link
              key={tab.key}
              to={tab.to}
              params={{ memberId }}
              className="whitespace-nowrap border-b-2 px-1 py-3 text-sm font-medium"
              activeProps={{
                className: 'border-blue-500 text-blue-600',
              }}
              inactiveProps={{
                className:
                  'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700',
              }}
            >
              {t(`members.profile.${tab.key}`)}
            </Link>
          ))}
        </nav>
      </div>

      <Outlet />
    </PageShell>
  )
}
