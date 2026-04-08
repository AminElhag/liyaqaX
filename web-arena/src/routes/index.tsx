import { createFileRoute } from '@tanstack/react-router'
import { useAuthStore } from '@/stores/useAuthStore'
import { useTranslation } from 'react-i18next'

export const Route = createFileRoute('/')({
  component: HomePage,
})

function HomePage() {
  const { member, portalSettings } = useAuthStore()
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'

  if (!member) return null

  const ms = member.membership

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-bold">
        {t('home.greeting', { name: isAr ? member.firstNameAr || member.firstName : member.firstName })}
      </h1>

      {portalSettings?.portalMessage && (
        <div className="rounded-lg bg-blue-50 p-4 text-sm text-blue-700">
          {portalSettings.portalMessage}
        </div>
      )}

      {ms && (
        <div className={`rounded-xl p-5 text-white ${
          ms.status === 'active' ? 'bg-green-600' :
          ms.status === 'frozen' ? 'bg-blue-600' :
          ms.status === 'expired' ? 'bg-red-600' : 'bg-gray-600'
        }`}>
          <div className="text-lg font-bold">{isAr ? ms.planNameAr : ms.planName}</div>
          <div className="text-sm opacity-90">{t(`status.${ms.status}`)}</div>
          <div className="mt-2 text-sm">
            {t('membership.daysRemaining', { days: ms.daysRemaining })}
          </div>
          {ms.daysRemaining < 7 && ms.daysRemaining > 0 && (
            <div className="mt-2 rounded bg-white/20 px-3 py-1 text-xs">
              {t('membership.renewReminder')}
            </div>
          )}
        </div>
      )}

      <div className="grid grid-cols-2 gap-3">
        {portalSettings?.gxBookingEnabled && (
          <a href="/classes" className="rounded-lg bg-white p-4 text-center shadow-sm">
            {t('home.bookClass')}
          </a>
        )}
        {portalSettings?.ptViewEnabled && (
          <a href="/sessions" className="rounded-lg bg-white p-4 text-center shadow-sm">
            {t('home.ptSessions')}
          </a>
        )}
        {portalSettings?.invoiceViewEnabled && (
          <a href="/payments/invoices" className="rounded-lg bg-white p-4 text-center shadow-sm">
            {t('home.invoices')}
          </a>
        )}
      </div>
    </div>
  )
}
