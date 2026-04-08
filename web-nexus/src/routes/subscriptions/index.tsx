import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getSubscriptionDashboard } from '@/api/subscriptions'
import { useState } from 'react'
import { cn } from '@/lib/cn'

export const Route = createFileRoute('/subscriptions/')({
  component: SubscriptionsDashboard,
})

function StatusBadge({ status }: { status: string }) {
  const { t } = useTranslation()
  const colors: Record<string, string> = {
    ACTIVE: 'bg-green-100 text-green-700',
    GRACE: 'bg-orange-100 text-orange-700',
    EXPIRED: 'bg-red-100 text-red-700',
    CANCELLED: 'bg-gray-100 text-gray-500',
  }
  return (
    <span className={cn('inline-block rounded-full px-2 py-0.5 text-xs font-medium', colors[status] ?? 'bg-gray-100 text-gray-500')}>
      {t(`subscription.status.${status.toLowerCase()}`)}
    </span>
  )
}

function SubscriptionsDashboard() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const pageSize = 20

  const { data, isLoading } = useQuery({
    queryKey: ['subscriptions-dashboard', page],
    queryFn: () => getSubscriptionDashboard(page, pageSize),
  })

  const subs = data?.subscriptions ?? []
  const totalCount = data?.totalCount ?? 0
  const totalPages = Math.ceil(totalCount / pageSize)

  const activeCount = subs.filter((s) => s.status === 'ACTIVE').length
  const graceCount = subs.filter((s) => s.status === 'GRACE').length
  const expiringCount = subs.filter((s) => s.status === 'ACTIVE' && s.daysUntilExpiry <= 30).length
  const expiredCount = subs.filter((s) => s.status === 'EXPIRED').length

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-gray-900">{t('subscription.page_title')}</h1>
        <Link
          to="/subscriptions/plans"
          className="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          {t('subscription.plan.catalog_title')}
        </Link>
      </div>

      <div className="grid grid-cols-4 gap-4">
        {[
          { label: t('subscription.kpi.active_clubs'), value: activeCount, color: 'text-green-600' },
          { label: t('subscription.kpi.grace_clubs'), value: graceCount, color: 'text-orange-600' },
          { label: t('subscription.kpi.expiring_soon'), value: expiringCount, color: 'text-amber-600' },
          { label: t('subscription.kpi.expired_clubs'), value: expiredCount, color: 'text-red-600' },
        ].map((kpi) => (
          <div key={kpi.label} className="rounded-lg border bg-white p-4">
            <p className="text-sm text-gray-500">{kpi.label}</p>
            <p className={cn('mt-1 text-2xl font-bold', kpi.color)}>{kpi.value}</p>
          </div>
        ))}
      </div>

      {isLoading ? (
        <p className="text-gray-500">{t('common.loading')}</p>
      ) : subs.length === 0 ? (
        <p className="text-gray-500">{t('common.no_results')}</p>
      ) : (
        <>
          <div className="overflow-hidden rounded-lg border bg-white">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">{t('subscription.table.club')}</th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">{t('subscription.table.plan')}</th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">{t('subscription.table.status')}</th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">{t('subscription.table.expires')}</th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">{t('subscription.table.price')}</th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {subs.map((sub) => (
                  <tr key={sub.clubId}>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-900">{sub.clubName}</td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-600">{sub.planName}</td>
                    <td className="whitespace-nowrap px-4 py-3"><StatusBadge status={sub.status} /></td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-600">
                      {new Date(sub.currentPeriodEnd).toLocaleDateString()}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-600">{sub.monthlyPriceSar} SAR</td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <Link
                        to="/subscriptions/$clubId"
                        params={{ clubId: sub.clubId }}
                        className="text-sm font-medium text-blue-600 hover:text-blue-700"
                      >
                        {t('common.edit')}
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="flex items-center justify-between">
              <button
                type="button"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="rounded border px-3 py-1 text-sm disabled:opacity-50"
              >
                Previous
              </button>
              <span className="text-sm text-gray-500">
                Page {page + 1} of {totalPages}
              </span>
              <button
                type="button"
                onClick={() => setPage((p) => p + 1)}
                disabled={page >= totalPages - 1}
                className="rounded border px-3 py-1 text-sm disabled:opacity-50"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
