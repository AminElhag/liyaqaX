import { createFileRoute, Link, Outlet, useMatches } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getClub } from '@/api/clubs'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { KpiCard } from '@/components/stats/KpiCard'
import { PermissionGate } from '@/components/common/PermissionGate'
import { Permission } from '@/types/permissions'
import { formatCurrency } from '@/lib/formatCurrency'

export const Route = createFileRoute(
  '/organizations/$orgId/clubs/$clubId',
)({
  component: ClubDetailLayout,
})

function ClubDetailLayout() {
  const matches = useMatches()
  const hasChildRoute = matches.some(
    (m) =>
      m.routeId.includes('/branches/'),
  )

  if (hasChildRoute) {
    return <Outlet />
  }

  return <ClubDetailPage />
}

function ClubDetailPage() {
  const { orgId, clubId } = Route.useParams()
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'

  const { data: club, isLoading } = useQuery({
    queryKey: ['club', orgId, clubId],
    queryFn: () => getClub(orgId, clubId),
    staleTime: 120_000,
  })

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (!club) {
    return null
  }

  return (
    <div className="p-6">
      <div className="mb-2">
        <Link
          to="/organizations/$orgId"
          params={{ orgId }}
          className="text-sm text-blue-600 hover:text-blue-800"
        >
          &larr; {t('common.back')}
        </Link>
      </div>

      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold text-gray-900">
            {isAr ? club.nameAr : club.nameEn}
          </h2>
          <p className="mt-1 text-sm text-gray-500">
            {t('clubs.detail')}
          </p>
        </div>
        <div className="flex gap-2">
          <PermissionGate permission={Permission.CLUB_UPDATE}>
            <button
              type="button"
              className="rounded-md border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              {t('common.edit')}
            </button>
          </PermissionGate>
          <PermissionGate permission={Permission.BRANCH_CREATE}>
            <button
              type="button"
              className="rounded-md bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
            >
              {t('clubs.add_branch')}
            </button>
          </PermissionGate>
        </div>
      </div>

      <div className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <KpiCard
          label={t('clubs.branches')}
          value={club.branchCount}
        />
        <KpiCard
          label={t('stats.active_members')}
          value={club.activeMemberCount}
        />
        <KpiCard
          label={t('stats.active_memberships')}
          value={club.activeMembershipCount}
        />
        <KpiCard
          label={t('clubs.mrr')}
          value={formatCurrency(club.estimatedMrrHalalas, i18n.language)}
          tooltip={t('stats.mrr_tooltip')}
        />
      </div>

      <div>
        <h3 className="mb-3 text-lg font-semibold text-gray-900">
          {t('clubs.branches')}
        </h3>

        {club.branches.length === 0 ? (
          <p className="text-sm text-gray-500">{t('common.no_results')}</p>
        ) : (
          <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {isAr ? t('common.name_ar') : t('common.name_en')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('stats.active_members')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('common.created_at')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {club.branches.map((branch) => (
                  <tr key={branch.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <Link
                        to="/organizations/$orgId/clubs/$clubId/branches/$branchId"
                        params={{ orgId, clubId, branchId: branch.id }}
                        className="text-sm font-medium text-blue-600 hover:text-blue-800"
                      >
                        {isAr ? branch.nameAr : branch.nameEn}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {branch.activeMemberCount}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-500">
                      {new Intl.DateTimeFormat(i18n.language, {
                        dateStyle: 'medium',
                        timeZone: 'Asia/Riyadh',
                      }).format(new Date(branch.createdAt))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
