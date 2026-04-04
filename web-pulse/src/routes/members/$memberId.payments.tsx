import { createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

export const Route = createFileRoute('/members/$memberId/payments')({
  component: PaymentsTab,
})

function PaymentsTab() {
  const { t } = useTranslation()
  return (
    <div className="rounded-lg border border-dashed border-gray-300 px-6 py-12 text-center">
      <p className="text-sm text-gray-500">{t('members.profile.comingSoon')}</p>
    </div>
  )
}
