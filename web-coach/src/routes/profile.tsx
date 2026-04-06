import { createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/useAuthStore'
import { cn } from '@/lib/cn'

export const Route = createFileRoute('/profile')({
  component: ProfilePage,
})

function ProfilePage() {
  const { t } = useTranslation()
  const trainer = useAuthStore((s) => s.trainer)

  if (!trainer) return null

  const today = new Date()
  const thirtyDaysFromNow = new Date(today.getTime() + 30 * 24 * 60 * 60 * 1000)

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-xl font-bold text-gray-900">{t('profile.title')}</h1>

      <div className="rounded-lg border border-gray-200 bg-white p-6">
        <div className="mb-4">
          <h2 className="text-lg font-semibold text-gray-900">
            {trainer.firstName} {trainer.lastName}
          </h2>
          {trainer.firstNameAr && (
            <p className="text-sm text-gray-500" dir="rtl">
              {trainer.firstNameAr} {trainer.lastNameAr}
            </p>
          )}
        </div>

        <div className="mb-4 space-y-2 text-sm text-gray-700">
          <p>{trainer.email}</p>
          {trainer.phone && <p>{trainer.phone}</p>}
          <p>{trainer.club.name}</p>
        </div>

        <div className="mb-6 flex gap-2">
          {trainer.trainerTypes.map((type) => (
            <span
              key={type}
              className={cn(
                'inline-flex rounded-full px-3 py-1 text-xs font-semibold',
                type === 'pt'
                  ? 'bg-teal-100 text-teal-800'
                  : 'bg-purple-100 text-purple-800',
              )}
            >
              {t(`profile.trainer_type.${type}`)}
            </span>
          ))}
        </div>

        <h3 className="mb-3 text-sm font-semibold text-gray-900">
          {t('profile.certifications')}
        </h3>

        {trainer.certifications.length === 0 ? (
          <p className="text-sm text-gray-500">No certifications</p>
        ) : (
          <div className="space-y-3">
            {trainer.certifications.map((cert) => {
              const expiring =
                cert.expiryDate && new Date(cert.expiryDate) <= thirtyDaysFromNow
              return (
                <div
                  key={cert.id}
                  className="rounded-md border border-gray-100 bg-gray-50 p-3"
                >
                  <div className="flex items-center justify-between">
                    <p className="text-sm font-medium text-gray-900">{cert.name}</p>
                    {expiring && (
                      <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">
                        {t('profile.expires_soon')}
                      </span>
                    )}
                  </div>
                  {cert.issuingOrganization && (
                    <p className="text-xs text-gray-500">{cert.issuingOrganization}</p>
                  )}
                  {cert.expiryDate && (
                    <p className="text-xs text-gray-400">
                      Expires: {new Date(cert.expiryDate).toLocaleDateString()}
                    </p>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
