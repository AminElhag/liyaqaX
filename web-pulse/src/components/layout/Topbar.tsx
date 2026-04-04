import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/useAuthStore'
import { BranchSelector } from './BranchSelector'

export function Topbar() {
  const { t, i18n } = useTranslation()
  const user = useAuthStore((s) => s.user)
  const clearAuth = useAuthStore((s) => s.clearAuth)

  const toggleLanguage = () => {
    i18n.changeLanguage(i18n.language === 'ar' ? 'en' : 'ar')
  }

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-gray-200 bg-white px-4">
      <BranchSelector />

      <div className="flex items-center gap-4">
        <button
          type="button"
          onClick={toggleLanguage}
          className="rounded-md px-2 py-1 text-sm text-gray-500 hover:bg-gray-100 hover:text-gray-700"
        >
          {i18n.language === 'ar' ? 'EN' : 'ع'}
        </button>

        <div className="flex items-center gap-2">
          <span className="text-sm text-gray-700">{user?.email}</span>
          <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700">
            {user?.roleName}
          </span>
        </div>

        <button
          type="button"
          onClick={clearAuth}
          className="rounded-md px-3 py-1.5 text-sm text-gray-500 hover:bg-gray-100 hover:text-gray-700"
        >
          {t('auth.logout')}
        </button>
      </div>
    </header>
  )
}
