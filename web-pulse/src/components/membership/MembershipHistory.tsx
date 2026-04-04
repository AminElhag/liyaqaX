import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { getMembershipHistory, membershipKeys } from '@/api/memberships'
import { MemberStatusBadge } from '@/components/members/MemberStatusBadge'
import { formatCurrency } from '@/lib/formatCurrency'
import { formatDate } from '@/lib/formatDate'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import type { MembershipStatus } from '@/types/domain'

interface MembershipHistoryProps {
  memberId: string
}

export function MembershipHistory({ memberId }: MembershipHistoryProps) {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'
  const locale = i18n.language

  const { data, isLoading } = useQuery({
    queryKey: membershipKeys.history(memberId, {}),
    queryFn: () => getMembershipHistory(memberId),
    staleTime: 2 * 60 * 1000,
  })

  if (isLoading) return <LoadingSkeleton rows={3} />

  if (!data || data.items.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-gray-300 px-6 py-8 text-center">
        <p className="text-sm text-gray-500">{t('membership.historyEmpty')}</p>
      </div>
    )
  }

  return (
    <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
      <table className="min-w-full divide-y divide-gray-200">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
              {t('membership.historyColumns.plan')}
            </th>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
              {t('membership.historyColumns.period')}
            </th>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
              {t('membership.historyColumns.amount')}
            </th>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
              {t('membership.historyColumns.method')}
            </th>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
              {t('membership.historyColumns.status')}
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {data.items.map((ms) => (
            <tr key={ms.id}>
              <td className="px-4 py-3 text-sm text-gray-900">
                {isAr ? ms.planNameAr : ms.planNameEn}
              </td>
              <td className="px-4 py-3 text-sm text-gray-500">
                {formatDate(ms.startDate, locale)} — {formatDate(ms.endDate, locale)}
              </td>
              <td className="px-4 py-3 text-sm text-gray-900">
                {formatCurrency(ms.amountHalalas, locale)}
              </td>
              <td className="px-4 py-3 text-sm text-gray-500">
                {ms.paymentMethod ? t(`membership.paymentMethods.${ms.paymentMethod}`) : '—'}
              </td>
              <td className="px-4 py-3">
                <MemberStatusBadge status={ms.status as MembershipStatus} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
