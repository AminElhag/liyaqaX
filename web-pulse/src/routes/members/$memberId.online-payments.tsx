import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  getMemberOnlinePayments,
  onlinePaymentKeys,
} from '@/api/onlinePayments'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { EmptyState } from '@/components/shared/EmptyState'
import { formatDateTime } from '@/lib/formatDate'

export const Route = createFileRoute('/members/$memberId/online-payments')({
  component: OnlinePaymentsTab,
})

function OnlinePaymentsTab() {
  const { memberId } = Route.useParams()
  const { t, i18n } = useTranslation()
  const locale = i18n.language

  const { data, isLoading } = useQuery({
    queryKey: onlinePaymentKeys.member(memberId),
    queryFn: () => getMemberOnlinePayments(memberId),
    staleTime: 2 * 60 * 1000,
  })

  if (isLoading) return <LoadingSkeleton rows={5} />

  const transactions = data?.transactions ?? []

  if (transactions.length === 0) {
    return <EmptyState message={t('member.online_payments_empty')} />
  }

  return (
    <div className="overflow-hidden rounded-lg border">
      <table className="min-w-full divide-y divide-gray-200">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
              {t('common.date')}
            </th>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
              {t('common.plan')}
            </th>
            <th className="px-4 py-3 text-end text-xs font-medium uppercase text-gray-500">
              {t('common.amount')}
            </th>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
              {t('common.method')}
            </th>
            <th className="px-4 py-3 text-center text-xs font-medium uppercase text-gray-500">
              {t('common.status')}
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200 bg-white">
          {transactions.map((tx) => (
            <tr key={tx.transactionId}>
              <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-500">
                {formatDateTime(tx.createdAt, locale)}
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-900">
                {tx.planName}
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-end text-sm text-gray-900">
                {tx.amountSar} SAR
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-500">
                {tx.paymentMethod ?? '—'}
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-center">
                <StatusBadge status={tx.status} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function StatusBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    PAID: 'bg-green-100 text-green-800',
    FAILED: 'bg-red-100 text-red-800',
    INITIATED: 'bg-gray-100 text-gray-600',
    CANCELLED: 'bg-gray-100 text-gray-600',
  }

  const labels: Record<string, string> = {
    PAID: 'Paid',
    FAILED: 'Failed',
    INITIATED: 'Initiated',
    CANCELLED: 'Cancelled',
  }

  return (
    <span
      className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${styles[status] ?? 'bg-gray-100 text-gray-600'}`}
    >
      {labels[status] ?? status}
    </span>
  )
}
