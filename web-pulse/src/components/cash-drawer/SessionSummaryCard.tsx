import { useTranslation } from 'react-i18next'
import { ShortSurplusBadge } from './ShortSurplusBadge'
import type { CashDrawerSession } from '@/types/domain'

interface SessionSummaryCardProps {
  session: CashDrawerSession
}

export function SessionSummaryCard({ session }: SessionSummaryCardProps) {
  const { t } = useTranslation()

  const rows = [
    { label: t('cash_drawer.opening_float'), value: `${session.openingFloat.sar} SAR` },
    { label: t('cash_drawer.total_cash_in'), value: `${session.totalCashIn.sar} SAR` },
    { label: t('cash_drawer.total_cash_out'), value: `${session.totalCashOut.sar} SAR` },
    {
      label: t('cash_drawer.expected_closing'),
      value: session.expectedClosing ? `${session.expectedClosing.sar} SAR` : '—',
    },
    {
      label: t('cash_drawer.counted_closing'),
      value: session.countedClosing ? `${session.countedClosing.sar} SAR` : '—',
    },
  ]

  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4">
      <h3 className="mb-3 text-sm font-semibold text-gray-900">
        {t('cash_drawer.session_summary')}
      </h3>
      <dl className="space-y-2">
        {rows.map((row) => (
          <div key={row.label} className="flex justify-between text-sm">
            <dt className="text-gray-500">{row.label}</dt>
            <dd className="font-medium text-gray-900">{row.value}</dd>
          </div>
        ))}
        <div className="flex items-center justify-between border-t pt-2 text-sm">
          <dt className="font-medium text-gray-700">
            {t('cash_drawer.difference_label')}
          </dt>
          <dd>
            <ShortSurplusBadge difference={session.difference} />
          </dd>
        </div>
      </dl>
    </div>
  )
}
