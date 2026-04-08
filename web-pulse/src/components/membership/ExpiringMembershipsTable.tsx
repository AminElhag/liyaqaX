import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import {
  getExpiringMemberships,
  membershipKeys,
  type ExpiringMembershipsParams,
} from '@/api/memberships'
import { formatDate } from '@/lib/formatDate'

interface ExpiringMembershipsTableProps {
  onRenew: (memberId: string, membershipId: string) => void
}

type FilterOption = 7 | 14 | 30 | 'overdue'

export function ExpiringMembershipsTable({ onRenew }: ExpiringMembershipsTableProps) {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'
  const locale = i18n.language
  const [activeFilter, setActiveFilter] = useState<FilterOption>(30)

  const queryParams: ExpiringMembershipsParams =
    activeFilter === 'overdue' ? { days: 0, size: 100 } : { days: activeFilter, size: 100 }

  const { data, isLoading } = useQuery({
    queryKey: membershipKeys.expiring(queryParams),
    queryFn: () => getExpiringMemberships(queryParams),
    refetchInterval: 300_000,
  })

  const memberships = data?.items ?? []

  const overdue = memberships.filter((m) => m.daysRemaining < 0)
  const upcoming = memberships.filter((m) => m.daysRemaining >= 0)

  const filters: { value: FilterOption; label: string }[] = [
    { value: 7, label: t('membership.filterDays', { days: 7 }) },
    { value: 14, label: t('membership.filterDays', { days: 14 }) },
    { value: 30, label: t('membership.filterDays', { days: 30 }) },
    { value: 'overdue', label: t('membership.overdue') },
  ]

  function daysRemainingColor(days: number): string {
    if (days < 0) return 'text-red-600 font-semibold'
    if (days < 7) return 'text-amber-600 font-medium'
    return 'text-green-600'
  }

  const thClass = 'px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500'
  const tdClass = 'whitespace-nowrap px-4 py-3 text-sm text-gray-900'

  function renderTable(items: typeof memberships, isOverdueSection: boolean) {
    if (items.length === 0) return null

    return (
      <div className={isOverdueSection ? 'mb-6' : ''}>
        {isOverdueSection && (
          <h3 className="mb-2 text-sm font-semibold text-red-700">{t('membership.overdue')}</h3>
        )}
        <div className="overflow-x-auto rounded-lg border border-gray-200">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className={thClass}>{t('common.name')}</th>
                <th className={thClass}>{t('membership.plan')}</th>
                <th className={thClass}>{t('membership.endDate')}</th>
                <th className={thClass}>{t('membership.daysRemainingColumn')}</th>
                <th className={thClass}>{t('common.phone')}</th>
                <th className={thClass} />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {items.map((membership) => (
                <tr key={membership.membershipId} className={isOverdueSection ? 'bg-red-50' : ''}>
                  <td className={tdClass}>{membership.memberName}</td>
                  <td className={tdClass}>
                    {isAr ? membership.planNameAr : membership.planNameEn}
                  </td>
                  <td className={tdClass}>{formatDate(membership.endDate, locale)}</td>
                  <td className={`${tdClass} ${daysRemainingColor(membership.daysRemaining)}`}>
                    {membership.daysRemaining}
                  </td>
                  <td className={tdClass}>{membership.memberPhone}</td>
                  <td className={tdClass}>
                    <button
                      type="button"
                      onClick={() => onRenew(membership.memberId, membership.membershipId)}
                      className="rounded-md bg-blue-600 px-3 py-1.5 text-xs font-medium text-white shadow-sm hover:bg-blue-700"
                    >
                      {t('membership.renewAction')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    )
  }

  return (
    <div>
      <h2 className="mb-4 text-lg font-semibold text-gray-900">{t('membership.expiringTitle')}</h2>

      <div className="mb-4 flex gap-2">
        {filters.map((filter) => (
          <button
            key={String(filter.value)}
            type="button"
            onClick={() => setActiveFilter(filter.value)}
            className={`rounded-md px-3 py-1.5 text-sm font-medium ${
              activeFilter === filter.value
                ? 'bg-blue-600 text-white'
                : 'border border-gray-300 bg-white text-gray-700 hover:bg-gray-50'
            }`}
          >
            {filter.label}
          </button>
        ))}
      </div>

      {isLoading && (
        <div className="py-8 text-center text-sm text-gray-500">{t('common.loading')}</div>
      )}

      {!isLoading && memberships.length === 0 && (
        <div className="py-8 text-center text-sm text-gray-500">{t('common.noResults')}</div>
      )}

      {!isLoading && (
        <>
          {renderTable(overdue, true)}
          {renderTable(upcoming, false)}
        </>
      )}
    </div>
  )
}
