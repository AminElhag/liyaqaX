import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  listClubsZatcaStatus,
  onboardClub,
  renewClubCsid,
} from '@/api/zatca'
import type { ZatcaClubStatus } from '@/api/zatca'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { EmptyState } from '@/components/common/EmptyState'
import { PermissionGate } from '@/components/common/PermissionGate'
import { Permission } from '@/types/permissions'

export const Route = createFileRoute('/zatca')({
  component: ZatcaPage,
})

const STATUS_COLORS: Record<string, string> = {
  not_onboarded: 'bg-gray-100 text-gray-700',
  pending: 'bg-gray-100 text-gray-700',
  compliance_issued: 'bg-yellow-100 text-yellow-800',
  compliance_checked: 'bg-yellow-100 text-yellow-800',
  active: 'bg-green-100 text-green-800',
  expired: 'bg-red-100 text-red-800',
  revoked: 'bg-red-100 text-red-800',
  failed: 'bg-red-100 text-red-800',
}

function StatusBadge({ status }: { status: string }) {
  const { t } = useTranslation()
  const colorClass = STATUS_COLORS[status] ?? 'bg-gray-100 text-gray-700'
  const key = `zatca.status.${status}` as const
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${colorClass}`}
    >
      {t(key)}
    </span>
  )
}

function ZatcaPage() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()

  const [dialogOpen, setDialogOpen] = useState(false)
  const [dialogMode, setDialogMode] = useState<'onboard' | 'renew'>('onboard')
  const [selectedClubId, setSelectedClubId] = useState<string | null>(null)
  const [otp, setOtp] = useState('')
  const [error, setError] = useState<string | null>(null)

  const { data: clubs, isLoading } = useQuery({
    queryKey: ['zatca-clubs'],
    queryFn: listClubsZatcaStatus,
    staleTime: 60_000,
  })

  const onboardMutation = useMutation({
    mutationFn: ({ clubId, otpValue }: { clubId: string; otpValue: string }) =>
      onboardClub(clubId, otpValue),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['zatca-clubs'] })
      closeDialog()
    },
    onError: (err: { detail?: string }) => {
      setError(err.detail ?? t('zatca.error'))
    },
  })

  const renewMutation = useMutation({
    mutationFn: ({ clubId, otpValue }: { clubId: string; otpValue: string }) =>
      renewClubCsid(clubId, otpValue),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['zatca-clubs'] })
      closeDialog()
    },
    onError: (err: { detail?: string }) => {
      setError(err.detail ?? t('zatca.error'))
    },
  })

  function openDialog(clubId: string, mode: 'onboard' | 'renew') {
    setSelectedClubId(clubId)
    setDialogMode(mode)
    setOtp('')
    setError(null)
    setDialogOpen(true)
  }

  function closeDialog() {
    setDialogOpen(false)
    setOtp('')
    setError(null)
  }

  function handleSubmit() {
    if (!selectedClubId || !otp.trim()) return
    if (dialogMode === 'onboard') {
      onboardMutation.mutate({ clubId: selectedClubId, otpValue: otp.trim() })
    } else {
      renewMutation.mutate({ clubId: selectedClubId, otpValue: otp.trim() })
    }
  }

  const isPending = onboardMutation.isPending || renewMutation.isPending

  return (
    <div className="p-6">
      <h2 className="mb-6 text-xl font-semibold text-gray-900">
        {t('zatca.title')}
      </h2>

      {isLoading && <LoadingSpinner />}

      {clubs && clubs.length === 0 && (
        <EmptyState message={t('zatca.no_clubs')} />
      )}

      {clubs && clubs.length > 0 && (
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                  {t('zatca.status.active')}
                </th>
                <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                  {t('zatca.environment')}
                </th>
                <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                  {t('zatca.expires')}
                </th>
                <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                  {t('common.actions')}
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {clubs.map((club: ZatcaClubStatus, idx: number) => (
                <tr key={idx} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <StatusBadge status={club.onboardingStatus} />
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600">
                    {club.environment && (
                      <span
                        className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
                          club.environment === 'production'
                            ? 'bg-blue-100 text-blue-800'
                            : 'bg-gray-100 text-gray-700'
                        }`}
                      >
                        {club.environment}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-500">
                    {club.csidExpiresAt
                      ? new Intl.DateTimeFormat(i18n.language, {
                          dateStyle: 'medium',
                          timeZone: 'Asia/Riyadh',
                        }).format(new Date(club.csidExpiresAt))
                      : '-'}
                  </td>
                  <td className="px-4 py-3">
                    <PermissionGate permission={Permission.ZATCA_ONBOARD}>
                      {(club.onboardingStatus === 'active' ||
                        club.onboardingStatus === 'expired') && (
                        <button
                          type="button"
                          onClick={() => openDialog('', 'renew')}
                          className="text-sm font-medium text-blue-600 hover:text-blue-800"
                        >
                          {t('zatca.renew')}
                        </button>
                      )}
                      {club.onboardingStatus !== 'active' &&
                        club.onboardingStatus !== 'expired' && (
                          <button
                            type="button"
                            onClick={() => openDialog('', 'onboard')}
                            className="text-sm font-medium text-blue-600 hover:text-blue-800"
                          >
                            {t('zatca.onboard')}
                          </button>
                        )}
                    </PermissionGate>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {dialogOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
            <h3 className="mb-2 text-lg font-semibold text-gray-900">
              {t('zatca.otp_dialog.title')}
            </h3>
            <p className="mb-4 text-sm text-gray-600">
              {t('zatca.otp_dialog.description')}
            </p>

            {error && (
              <div className="mb-4 rounded-md bg-red-50 p-3 text-sm text-red-700">
                {error}
              </div>
            )}

            <label className="mb-1 block text-sm font-medium text-gray-700">
              {t('zatca.otp_dialog.otp_label')}
            </label>
            <input
              type="text"
              value={otp}
              onChange={(e) => setOtp(e.target.value)}
              className="mb-4 w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              placeholder="123456"
              disabled={isPending}
            />

            <div className="flex justify-end gap-3">
              <button
                type="button"
                onClick={closeDialog}
                className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                disabled={isPending}
              >
                {t('common.cancel')}
              </button>
              <button
                type="button"
                onClick={handleSubmit}
                disabled={isPending || !otp.trim()}
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
              >
                {isPending
                  ? t('common.loading')
                  : dialogMode === 'onboard'
                    ? t('zatca.otp_dialog.submit')
                    : t('zatca.otp_dialog.submit_renew')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
