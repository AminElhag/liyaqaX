import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/useAuthStore'

export function LapsedBanner() {
  const { t } = useTranslation()
  const { portalSettings } = useAuthStore()

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-white">
      <div className="mx-4 max-w-md space-y-4 text-center">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-red-100">
          <span className="text-2xl text-red-600">!</span>
        </div>
        <h1 className="text-xl font-bold text-gray-900">
          {t('lapsed.banner_title')}
        </h1>
        <p className="text-sm text-gray-600">
          {t('lapsed.banner_body')}
        </p>
        {portalSettings?.portalMessage && (
          <p className="text-sm text-gray-500">
            {portalSettings.portalMessage}
          </p>
        )}
      </div>
    </div>
  )
}
