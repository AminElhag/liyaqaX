import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getMyClubZatcaStatus } from '@/api/zatca'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'

export const Route = createFileRoute('/settings/zatca')({
  component: ZatcaSettingsPage,
})

const STATUS_COLORS: Record<string, string> = {
  not_onboarded: 'bg-gray-100 text-gray-700',
  pending: 'bg-gray-100 text-gray-700',
  compliance_issued: 'bg-yellow-100 text-yellow-800',
  compliance_checked: 'bg-yellow-100 text-yellow-800',
  active: 'bg-green-100 text-green-800',
  expired: 'bg-red-100 text-red-800',
  failed: 'bg-red-100 text-red-800',
}

function ZatcaSettingsPage() {
  const { t, i18n } = useTranslation()

  const { data: status, isLoading } = useQuery({
    queryKey: ['zatca-status'],
    queryFn: getMyClubZatcaStatus,
    staleTime: 60_000,
  })

  if (isLoading) return <LoadingSpinner />

  const onboardingStatus = status?.onboardingStatus ?? 'not_onboarded'
  const colorClass =
    STATUS_COLORS[onboardingStatus] ?? 'bg-gray-100 text-gray-700'
  const statusKey = `zatca.status.${onboardingStatus}` as const

  return (
    <div className="p-6">
      <h2 className="mb-6 text-xl font-semibold text-gray-900">
        {t('zatca.title')}
      </h2>

      <div className="max-w-lg rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-gray-500">
              {t('zatca.status')}
            </span>
            <span
              className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${colorClass}`}
            >
              {t(statusKey)}
            </span>
          </div>

          {status?.environment && (
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-gray-500">
                {t('zatca.environment')}
              </span>
              <span className="text-sm text-gray-900">
                {status.environment}
              </span>
            </div>
          )}

          {status?.csidExpiresAt && (
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-gray-500">
                {t('zatca.expires')}
              </span>
              <span className="text-sm text-gray-900">
                {new Intl.DateTimeFormat(i18n.language, {
                  dateStyle: 'long',
                  timeZone: 'Asia/Riyadh',
                }).format(new Date(status.csidExpiresAt))}
              </span>
            </div>
          )}
        </div>

        {onboardingStatus === 'not_onboarded' && (
          <div className="mt-4 rounded-md bg-blue-50 p-3 text-sm text-blue-700">
            {t('zatca.not_onboarded')}
          </div>
        )}
      </div>
    </div>
  )
}
