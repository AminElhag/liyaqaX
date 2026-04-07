import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

export const Route = createFileRoute('/auth/register/success')({
  component: RegistrationSuccess,
})

function RegistrationSuccess() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm space-y-6 text-center">
        <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-green-100">
          <svg className="h-10 w-10 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <h1 className="text-2xl font-bold">{t('register.success.title')}</h1>
        <p className="text-gray-500">{t('register.success.body')}</p>
        <button
          onClick={() => navigate({ to: '/auth/login' })}
          className="w-full rounded-lg bg-blue-600 py-3 font-semibold text-white"
        >
          {t('register.success.backToLogin')}
        </button>
      </div>
    </div>
  )
}
