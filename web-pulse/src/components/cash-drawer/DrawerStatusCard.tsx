import { useTranslation } from 'react-i18next'
import type { CashDrawerSession } from '@/types/domain'

interface DrawerStatusCardProps {
  session: CashDrawerSession
}

export function DrawerStatusCard({ session }: DrawerStatusCardProps) {
  const { t } = useTranslation()

  const openedAt = new Date(session.openedAt)
  const now = new Date()
  const hoursOpen = Math.floor((now.getTime() - openedAt.getTime()) / (1000 * 60 * 60))
  const minutesOpen = Math.floor(
    ((now.getTime() - openedAt.getTime()) % (1000 * 60 * 60)) / (1000 * 60),
  )

  return (
    <div className="rounded-lg border border-green-200 bg-green-50 p-4">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-green-800">
          {t('cash_drawer.session_open')}
        </h3>
        <span className="rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-700">
          {hoursOpen}h {minutesOpen}m
        </span>
      </div>
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <div>
          <p className="text-xs text-green-600">{t('cash_drawer.opening_float')}</p>
          <p className="text-lg font-semibold text-green-900">
            {session.openingFloat.sar} SAR
          </p>
        </div>
        <div>
          <p className="text-xs text-green-600">{t('cash_drawer.total_cash_in')}</p>
          <p className="text-lg font-semibold text-green-900">
            {session.totalCashIn.sar} SAR
          </p>
        </div>
        <div>
          <p className="text-xs text-green-600">{t('cash_drawer.total_cash_out')}</p>
          <p className="text-lg font-semibold text-green-900">
            {session.totalCashOut.sar} SAR
          </p>
        </div>
        <div>
          <p className="text-xs text-green-600">{t('cash_drawer.entries')}</p>
          <p className="text-lg font-semibold text-green-900">{session.entryCount}</p>
        </div>
      </div>
      <p className="mt-3 text-xs text-green-600">
        {t('cash_drawer.opened_by')}: {session.openedBy.firstName}{' '}
        {session.openedBy.lastName} —{' '}
        {openedAt.toLocaleString()}
      </p>
    </div>
  )
}
