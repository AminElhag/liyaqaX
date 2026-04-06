import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getStats } from '@/api/stats'
import { KpiCard } from '@/components/stats/KpiCard'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { formatCurrency } from '@/lib/formatCurrency'
import { PermissionGate } from '@/components/common/PermissionGate'
import { Permission } from '@/types/permissions'

export const Route = createFileRoute('/')({
  component: HomePage,
})

function HomePage() {
  const { t, i18n } = useTranslation()

  const { data: stats, isLoading, refetch } = useQuery({
    queryKey: ['platform-stats'],
    queryFn: getStats,
    staleTime: 60_000,
  })

  if (isLoading) {
    return <LoadingSpinner />
  }

  return (
    <PermissionGate permission={Permission.PLATFORM_STATS_VIEW}>
      <div className="p-6">
        <div className="mb-6 flex items-center justify-between">
          <h2 className="text-xl font-semibold text-gray-900">
            {t('stats.title')}
          </h2>
          <button
            type="button"
            onClick={() => refetch()}
            className="rounded-md border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            {t('common.refresh')}
          </button>
        </div>

        {stats && (
          <>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <KpiCard
                label={t('stats.total_orgs')}
                value={stats.totalOrganizations.toLocaleString(i18n.language)}
              />
              <KpiCard
                label={t('stats.total_clubs')}
                value={stats.totalClubs.toLocaleString(i18n.language)}
              />
              <KpiCard
                label={t('stats.total_branches')}
                value={stats.totalBranches.toLocaleString(i18n.language)}
              />
              <KpiCard
                label={t('stats.active_members')}
                value={stats.activeMembers.toLocaleString(i18n.language)}
              />
              <KpiCard
                label={t('stats.active_memberships')}
                value={stats.activeMemberships.toLocaleString(i18n.language)}
              />
              <KpiCard
                label={t('stats.estimated_mrr')}
                value={formatCurrency(stats.estimatedMrrHalalas, i18n.language)}
                tooltip={t('stats.mrr_tooltip')}
              />
            </div>

            <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <KpiCard
                label={t('stats.new_members_30d')}
                value={stats.newMembersLast30Days.toLocaleString(i18n.language)}
              />
            </div>

            <p className="mt-6 text-xs text-gray-400">
              {t('stats.generated_at', {
                time: new Intl.DateTimeFormat(i18n.language, {
                  dateStyle: 'medium',
                  timeStyle: 'short',
                  timeZone: 'Asia/Riyadh',
                }).format(new Date(stats.generatedAt)),
              })}
            </p>
          </>
        )}
      </div>
    </PermissionGate>
  )
}
