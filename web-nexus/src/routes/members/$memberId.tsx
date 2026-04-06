import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getMember } from '@/api/members'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { StatusBadge } from '@/components/common/StatusBadge'

export const Route = createFileRoute('/members/$memberId')({
  component: MemberDetailPage,
})

function MemberDetailPage() {
  const { memberId } = Route.useParams()
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'

  const { data: member, isLoading } = useQuery({
    queryKey: ['member', memberId],
    queryFn: () => getMember(memberId),
    staleTime: 120_000,
  })

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (!member) {
    return null
  }

  return (
    <div className="p-6">
      <div className="mb-2">
        <Link
          to="/members"
          className="text-sm text-blue-600 hover:text-blue-800"
        >
          &larr; {t('common.back')}
        </Link>
      </div>

      <div className="mb-6 flex items-center gap-3">
        <h2 className="text-xl font-semibold text-gray-900">
          {isAr ? member.fullNameAr : member.fullNameEn}
        </h2>
        <StatusBadge status={member.membershipStatus} />
      </div>

      <div className="rounded-lg border border-gray-200 bg-white p-5 shadow-sm">
        <h3 className="mb-4 text-sm font-semibold text-gray-700">
          {t('members.detail')}
        </h3>
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <dt className="text-sm font-medium text-gray-500">{t('common.name_en')}</dt>
            <dd className="mt-1 text-sm text-gray-900">{member.fullNameEn}</dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-gray-500">{t('common.name_ar')}</dt>
            <dd className="mt-1 text-sm text-gray-900">{member.fullNameAr}</dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-gray-500">{t('members.phone')}</dt>
            <dd className="mt-1 text-sm text-gray-900">{member.phone}</dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-gray-500">{t('members.email')}</dt>
            <dd className="mt-1 text-sm text-gray-900">{member.email ?? '-'}</dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-gray-500">{t('members.organization')}</dt>
            <dd className="mt-1 text-sm text-gray-900">
              {isAr ? member.organizationNameAr : member.organizationNameEn}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-gray-500">{t('members.club')}</dt>
            <dd className="mt-1 text-sm text-gray-900">
              {isAr ? member.clubNameAr : member.clubNameEn}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-gray-500">{t('members.status')}</dt>
            <dd className="mt-1">
              <StatusBadge status={member.membershipStatus} />
            </dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-gray-500">{t('common.created_at')}</dt>
            <dd className="mt-1 text-sm text-gray-900">
              {new Intl.DateTimeFormat(i18n.language, {
                dateStyle: 'medium',
                timeStyle: 'short',
                timeZone: 'Asia/Riyadh',
              }).format(new Date(member.createdAt))}
            </dd>
          </div>
        </dl>
      </div>
    </div>
  )
}
