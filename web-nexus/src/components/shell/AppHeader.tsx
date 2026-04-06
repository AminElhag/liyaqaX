import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/useAuthStore'
import { logout } from '@/api/auth'

export function AppHeader() {
  const { t, i18n } = useTranslation()
  const user = useAuthStore((s) => s.user)
  const clearAuth = useAuthStore((s) => s.clearAuth)

  const toggleLanguage = () => {
    i18n.changeLanguage(i18n.language === 'ar' ? 'en' : 'ar')
  }

  const handleLogout = async () => {
    try {
      await logout()
    } finally {
      clearAuth()
      window.location.href = '/auth/login'
    }
  }

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-gray-200 bg-white px-4">
      <h1 className="text-sm font-semibold text-gray-900">
        {t('app.title')}
      </h1>

      <div className="flex items-center gap-4">
        {user && (
          <span className="text-xs text-gray-500">{user.email}</span>
        )}

        <button
          type="button"
          onClick={toggleLanguage}
          className="rounded px-2 py-1 text-xs text-gray-600 hover:bg-gray-100"
        >
          {i18n.language === 'ar' ? 'EN' : 'AR'}
        </button>

        <button
          type="button"
          onClick={handleLogout}
          className="rounded px-2 py-1 text-xs text-red-600 hover:bg-red-50"
        >
          {t('nav.logout')}
        </button>
      </div>
    </header>
  )
}
