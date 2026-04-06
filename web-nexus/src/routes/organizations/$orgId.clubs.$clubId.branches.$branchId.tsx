import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getBranch } from '@/api/branches'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { KpiCard } from '@/components/stats/KpiCard'

export const Route = createFileRoute(
  '/organizations/$orgId/clubs/$clubId/branches/$branchId',
)({
  component: BranchDetailPage,
})

function BranchDetailPage() {
  const { orgId, clubId, branchId } = Route.useParams()
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'

  const { data: branch, isLoading } = useQuery({
    queryKey: ['branch', orgId, clubId, branchId],
    queryFn: () => getBranch(orgId, clubId, branchId),
    staleTime: 120_000,
  })

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (!branch) {
    return null
  }

  return (
    <div className="p-6">
      <div className="mb-2">
        <Link
          to="/organizations/$orgId/clubs/$clubId"
          params={{ orgId, clubId }}
          className="text-sm text-blue-600 hover:text-blue-800"
        >
          &larr; {t('common.back')}
        </Link>
      </div>

      <div className="mb-6">
        <h2 className="text-xl font-semibold text-gray-900">
          {isAr ? branch.nameAr : branch.nameEn}
        </h2>
        <p className="mt-1 text-sm text-gray-500">
          {t('branches.detail')}
        </p>
      </div>

      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <KpiCard
          label={t('stats.active_members')}
          value={branch.activeMemberCount}
        />
        <KpiCard
          label={t('clubs.staff')}
          value={branch.staffCount}
        />
        <KpiCard
          label={t('branches.trainers')}
          value={branch.trainerCount}
        />
      </div>

      <div className="rounded-lg border border-gray-200 bg-white p-5 shadow-sm">
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <dt className="text-sm font-medium text-gray-500">{t('common.name_en')}</dt>
            <dd className="mt-1 text-sm text-gray-900">{branch.nameEn}</dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-gray-500">{t('common.name_ar')}</dt>
            <dd className="mt-1 text-sm text-gray-900">{branch.nameAr}</dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-gray-500">{t('common.created_at')}</dt>
            <dd className="mt-1 text-sm text-gray-900">
              {new Intl.DateTimeFormat(i18n.language, {
                dateStyle: 'medium',
                timeStyle: 'short',
                timeZone: 'Asia/Riyadh',
              }).format(new Date(branch.createdAt))}
            </dd>
          </div>
        </dl>
      </div>
    </div>
  )
}
