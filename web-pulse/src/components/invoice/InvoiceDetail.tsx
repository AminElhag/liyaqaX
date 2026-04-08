import { useTranslation } from 'react-i18next'
import { formatCurrency } from '@/lib/formatCurrency'
import { formatDateTime } from '@/lib/formatDate'
import type { Invoice } from '@/types/domain'
import { InvoiceQrCode } from './InvoiceQrCode'
import { InvoicePrintView } from './InvoicePrintView'
import { ZatcaStatusBadge } from './ZatcaStatusBadge'

interface InvoiceDetailProps {
  invoice: Invoice
  sellerNameAr?: string
  vatNumber?: string
}

export function InvoiceDetail({
  invoice,
  sellerNameAr,
  vatNumber,
}: InvoiceDetailProps) {
  const { t, i18n } = useTranslation()
  const locale = i18n.language

  function handlePrint() {
    window.print()
  }

  return (
    <>
      <div className="space-y-6 print:hidden">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">
            {t('invoice.title')} — {invoice.invoiceNumber}
          </h2>
          <div className="flex items-center gap-3">
            <ZatcaStatusBadge status={invoice.zatcaStatus} />
            <button
              onClick={handlePrint}
              className="rounded-md bg-gray-900 px-3 py-1.5 text-sm font-medium text-white hover:bg-gray-800"
            >
              {t('invoice.print')}
            </button>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-6">
          <div className="space-y-4 rounded-lg border p-4">
            <Row label={t('invoice.number')} value={invoice.invoiceNumber} />
            <Row
              label={t('invoice.date')}
              value={formatDateTime(invoice.issuedAt, locale)}
            />
            <Row label={t('invoice.buyerName')} value={invoice.memberName} />
            <Row label={t('invoice.paymentMethod')} value={invoice.paymentMethod} />
            {invoice.invoiceCounterValue != null && (
              <Row
                label={t('invoice.counter')}
                value={String(invoice.invoiceCounterValue)}
              />
            )}
          </div>

          <div className="space-y-4 rounded-lg border p-4">
            <Row
              label={t('invoice.subtotal')}
              value={formatCurrency(invoice.subtotalHalalas, locale)}
            />
            <Row
              label={t('invoice.vatRate')}
              value={`${(invoice.vatRate * 100).toFixed(0)}%`}
            />
            <Row
              label={t('invoice.vatAmount')}
              value={formatCurrency(invoice.vatAmountHalalas, locale)}
            />
            <div className="border-t pt-3">
              <Row
                label={t('invoice.total')}
                value={formatCurrency(invoice.totalHalalas, locale)}
                bold
              />
            </div>
          </div>
        </div>

        <div className="flex justify-center rounded-lg border p-6">
          <InvoiceQrCode qrCode={invoice.zatcaQrCode} />
        </div>
      </div>

      <InvoicePrintView
        invoice={invoice}
        sellerNameAr={sellerNameAr}
        vatNumber={vatNumber}
      />
    </>
  )
}

function Row({
  label,
  value,
  bold = false,
}: {
  label: string
  value: string
  bold?: boolean
}) {
  return (
    <div className="flex justify-between text-sm">
      <span className="text-gray-500">{label}</span>
      <span className={bold ? 'font-semibold' : ''}>{value}</span>
    </div>
  )
}
