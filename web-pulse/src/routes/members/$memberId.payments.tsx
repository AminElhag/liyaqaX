import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getMemberInvoices, getMemberInvoice, invoiceKeys } from '@/api/invoices'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { EmptyState } from '@/components/shared/EmptyState'
import { ZatcaStatusBadge } from '@/components/invoice/ZatcaStatusBadge'
import { InvoiceDetail } from '@/components/invoice/InvoiceDetail'
import { formatCurrency } from '@/lib/formatCurrency'
import { formatDateTime } from '@/lib/formatDate'

export const Route = createFileRoute('/members/$memberId/payments')({
  component: PaymentsTab,
})

function PaymentsTab() {
  const { memberId } = Route.useParams()
  const { t, i18n } = useTranslation()
  const locale = i18n.language
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<string | null>(null)

  const { data: invoicePage, isLoading } = useQuery({
    queryKey: invoiceKeys.memberInvoices(memberId, {}),
    queryFn: () => getMemberInvoices(memberId),
    staleTime: 2 * 60 * 1000,
  })

  const { data: selectedInvoice, isLoading: isDetailLoading } = useQuery({
    queryKey: invoiceKeys.detail(memberId, selectedInvoiceId ?? ''),
    queryFn: () => getMemberInvoice(memberId, selectedInvoiceId!),
    enabled: selectedInvoiceId != null,
    staleTime: 5 * 60 * 1000,
  })

  if (isLoading) return <LoadingSkeleton rows={5} />

  const invoices = invoicePage?.items ?? []

  if (selectedInvoiceId && selectedInvoice) {
    return (
      <div className="space-y-4">
        <button
          type="button"
          onClick={() => setSelectedInvoiceId(null)}
          className="text-sm text-blue-600 hover:text-blue-800"
        >
          &larr; {t('common.goBack')}
        </button>
        <InvoiceDetail invoice={selectedInvoice} />
      </div>
    )
  }

  if (selectedInvoiceId && isDetailLoading) {
    return <LoadingSkeleton rows={8} />
  }

  if (invoices.length === 0) {
    return <EmptyState message={t('members.profile.comingSoon')} />
  }

  return (
    <div className="overflow-hidden rounded-lg border">
      <table className="min-w-full divide-y divide-gray-200">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
              {t('invoice.number')}
            </th>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
              {t('invoice.date')}
            </th>
            <th className="px-4 py-3 text-end text-xs font-medium uppercase text-gray-500">
              {t('invoice.total')}
            </th>
            <th className="px-4 py-3 text-center text-xs font-medium uppercase text-gray-500">
              ZATCA
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200 bg-white">
          {invoices.map((invoice) => (
            <tr
              key={invoice.id}
              onClick={() => setSelectedInvoiceId(invoice.id)}
              className="cursor-pointer hover:bg-gray-50"
            >
              <td className="whitespace-nowrap px-4 py-3 text-sm font-medium text-gray-900">
                {invoice.invoiceNumber}
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-500">
                {formatDateTime(invoice.issuedAt, locale)}
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-end text-sm text-gray-900">
                {formatCurrency(invoice.totalHalalas, locale)}
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-center">
                <ZatcaStatusBadge status={invoice.zatcaStatus} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
