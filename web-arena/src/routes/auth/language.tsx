import { createFileRoute } from '@tanstack/react-router'
import { apiClient } from '@/api/client'
import { useAuthStore } from '@/stores/useAuthStore'
import { useTranslation } from 'react-i18next'

export const Route = createFileRoute('/auth/language')({
  component: LanguagePage,
})

function LanguagePage() {
  const { member, setMember } = useAuthStore()
  const { i18n } = useTranslation()

  const selectLanguage = async (lang: 'ar' | 'en') => {
    await apiClient.patch('/arena/profile', { preferredLanguage: lang })
    if (member) {
      setMember({ ...member, preferredLanguage: lang })
    }
    i18n.changeLanguage(lang)
    window.location.href = '/'
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm space-y-6 text-center">
        <h1 className="text-2xl font-bold">Choose your language</h1>
        <h2 className="text-2xl font-bold">اختر لغتك</h2>
        <div className="grid gap-4">
          <button
            onClick={() => selectLanguage('ar')}
            className="rounded-xl border-2 border-gray-200 p-6 text-xl font-semibold transition hover:border-primary hover:bg-primary/10"
          >
            العربية
          </button>
          <button
            onClick={() => selectLanguage('en')}
            className="rounded-xl border-2 border-gray-200 p-6 text-xl font-semibold transition hover:border-primary hover:bg-primary/10"
          >
            English
          </button>
        </div>
      </div>
    </div>
  )
}
