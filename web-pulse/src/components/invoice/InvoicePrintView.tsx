import { formatCurrency } from '@/lib/formatCurrency'
import { formatDateTime } from '@/lib/formatDate'
import type { Invoice } from '@/types/domain'
import { InvoiceQrCode } from './InvoiceQrCode'

interface InvoicePrintViewProps {
  invoice: Invoice
  sellerNameAr?: string
  vatNumber?: string
}

export function InvoicePrintView({
  invoice,
  sellerNameAr,
  vatNumber,
}: InvoicePrintViewProps) {
  return (
    <div
      className="hidden print:block"
      dir="rtl"
      lang="ar"
    >
      <div className="mx-auto max-w-[600px] p-8 font-sans text-sm">
        <h1 className="mb-6 text-center text-xl font-bold">
          {'\u0641\u0627\u062a\u0648\u0631\u0629 \u0636\u0631\u064a\u0628\u064a\u0629 \u0645\u0628\u0633\u0637\u0629'}
        </h1>

        <table className="mb-6 w-full border-collapse">
          <tbody>
            <PrintRow
              label={'\u0631\u0642\u0645 \u0627\u0644\u0641\u0627\u062a\u0648\u0631\u0629'}
              value={invoice.invoiceNumber}
            />
            <PrintRow
              label={'\u062a\u0627\u0631\u064a\u062e \u0648\u0648\u0642\u062a \u0627\u0644\u0641\u0627\u062a\u0648\u0631\u0629'}
              value={formatDateTime(invoice.issuedAt, 'ar')}
            />
            {sellerNameAr && (
              <PrintRow
                label={'\u0627\u0633\u0645 \u0627\u0644\u0628\u0627\u0626\u0639'}
                value={sellerNameAr}
              />
            )}
            {vatNumber && (
              <PrintRow
                label={'\u0627\u0644\u0631\u0642\u0645 \u0627\u0644\u0636\u0631\u064a\u0628\u064a \u0644\u0644\u0628\u0627\u0626\u0639'}
                value={vatNumber}
              />
            )}
            <PrintRow
              label={'\u0627\u0633\u0645 \u0627\u0644\u0645\u0634\u062a\u0631\u064a'}
              value={invoice.memberName}
            />
            <PrintRow
              label={'\u0648\u0635\u0641 \u0627\u0644\u062e\u062f\u0645\u0629'}
              value={'\u0627\u0634\u062a\u0631\u0627\u0643 \u0639\u0636\u0648\u064a\u0629 \u0646\u0627\u062f\u064a \u0631\u064a\u0627\u0636\u064a'}
            />
          </tbody>
        </table>

        <table className="mb-6 w-full border-collapse">
          <tbody>
            <PrintRow
              label={'\u0627\u0644\u0645\u0628\u0644\u063a \u0642\u0628\u0644 \u0627\u0644\u0636\u0631\u064a\u0628\u0629'}
              value={formatCurrency(invoice.subtotalHalalas, 'ar')}
            />
            <PrintRow
              label={'\u0646\u0633\u0628\u0629 \u0636\u0631\u064a\u0628\u0629 \u0627\u0644\u0642\u064a\u0645\u0629 \u0627\u0644\u0645\u0636\u0627\u0641\u0629'}
              value="15%"
            />
            <PrintRow
              label={'\u0645\u0628\u0644\u063a \u0627\u0644\u0636\u0631\u064a\u0628\u0629'}
              value={formatCurrency(invoice.vatAmountHalalas, 'ar')}
            />
            <PrintRow
              label={'\u0627\u0644\u0625\u062c\u0645\u0627\u0644\u064a \u0634\u0627\u0645\u0644 \u0627\u0644\u0636\u0631\u064a\u0628\u0629'}
              value={formatCurrency(invoice.totalHalalas, 'ar')}
              bold
            />
          </tbody>
        </table>

        <div className="flex justify-center">
          <InvoiceQrCode qrCode={invoice.zatcaQrCode} size={150} />
        </div>
      </div>
    </div>
  )
}

function PrintRow({
  label,
  value,
  bold = false,
}: {
  label: string
  value: string
  bold?: boolean
}) {
  return (
    <tr>
      <td className="border border-gray-300 px-3 py-2 text-gray-600">
        {label}
      </td>
      <td
        className={`border border-gray-300 px-3 py-2 ${bold ? 'font-bold' : ''}`}
      >
        {value}
      </td>
    </tr>
  )
}
