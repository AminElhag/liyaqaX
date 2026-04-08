import { useTranslation } from 'react-i18next'
import { useGraceStore } from '@/stores/useGraceStore'

export function GracePeriodBanner() {
  const { t } = useTranslation()
  const { isGrace, daysRemaining } = useGraceStore()

  if (!isGrace) return null

  return (
    <div className="flex items-center justify-center gap-2 bg-amber-50 border-b border-amber-200 px-4 py-2 text-sm text-amber-800">
      <span className="font-medium">&#9888;</span>
      <span>
        {t('subscription.grace_banner', { days: daysRemaining })}
      </span>
    </div>
  )
}
