import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/useAuthStore'
import { NotificationBell } from '@/components/notifications/NotificationBell'
import { NotificationDrawer } from '@/components/notifications/NotificationDrawer'

export function AppHeader() {
  const { t, i18n } = useTranslation()
  const { trainer, clearAuth } = useAuthStore()
  const [drawerOpen, setDrawerOpen] = useState(false)

  const toggleLanguage = () => {
    i18n.changeLanguage(i18n.language === 'ar' ? 'en' : 'ar')
  }

  return (
    <header className="flex h-14 items-center justify-between border-b border-gray-200 bg-white px-6">
      <span className="text-sm font-medium text-gray-700">
        {trainer ? `${trainer.firstName} ${trainer.lastName}` : ''}
      </span>
      <div className="flex items-center gap-3">
        <NotificationBell onClick={() => setDrawerOpen(true)} />
        <NotificationDrawer isOpen={drawerOpen} onClose={() => setDrawerOpen(false)} />
        <button
          onClick={toggleLanguage}
          className="rounded-md px-2 py-1 text-xs font-medium text-gray-600 hover:bg-gray-100"
        >
          {i18n.language === 'ar' ? 'EN' : 'عربي'}
        </button>
        <button
          onClick={clearAuth}
          className="rounded-md px-3 py-1 text-xs font-medium text-red-600 hover:bg-red-50"
        >
          {t('auth.logout')}
        </button>
      </div>
    </header>
  )
}
