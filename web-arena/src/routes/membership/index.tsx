import { createFileRoute } from '@tanstack/react-router'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/useAuthStore'
import { initiatePayment } from '@/api/payments'
import type { ApiError } from '@/types/api'

export const Route = createFileRoute('/membership/')({
  component: MembershipPage,
})

function MembershipPage() {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'
  const member = useAuthStore((s) => s.member)
  const portalSettings = useAuthStore((s) => s.portalSettings)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const membership = member?.membership

  const isPayable =
    membership?.status === 'pending_payment' ||
    member?.memberStatus === 'lapsed'

  const showPayNow =
    portalSettings?.onlinePaymentEnabled === true && isPayable

  const payMutation = useMutation({
    mutationFn: initiatePayment,
    onSuccess: (data) => {
      window.location.href = data.hostedUrl
    },
    onError: (error: ApiError) => {
      if (error.status === 403) {
        setErrorMessage(t('payment.disabled_message'))
      } else if (error.status === 409) {
        setErrorMessage(null)
      } else {
        setErrorMessage(error.detail || t('common.error'))
      }
    },
  })

  const handlePayNow = () => {
    if (!membership) return
    setErrorMessage(null)

    const membershipId = (membership as { id?: string }).id
    if (!membershipId) return

    payMutation.mutate({ membershipPublicId: membershipId })
  }

  if (!member) {
    return (
      <div className="p-4 text-center text-gray-500">
        {t('common.loading')}
      </div>
    )
  }

  return (
    <div className="space-y-4 p-4">
      <h1 className="text-lg font-semibold">{t('membership.title')}</h1>

      {membership ? (
        <div className="rounded-lg border bg-white p-4 shadow-sm">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-sm font-medium text-gray-500">
              {t('membership.plan')}
            </span>
            <span className="text-sm font-semibold">
              {isAr ? membership.planNameAr : membership.planName}
            </span>
          </div>
          <div className="mb-2 flex items-center justify-between">
            <span className="text-sm font-medium text-gray-500">
              {t('membership.status')}
            </span>
            <MembershipStatusBadge status={membership.status} />
          </div>
          <div className="mb-2 flex items-center justify-between">
            <span className="text-sm font-medium text-gray-500">
              {t('membership.startDate')}
            </span>
            <span className="text-sm">{membership.startDate}</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-sm font-medium text-gray-500">
              {t('membership.expiresAt')}
            </span>
            <span className="text-sm">{membership.expiresAt}</span>
          </div>
          {membership.daysRemaining > 0 && membership.status === 'active' && (
            <p className="mt-2 text-xs text-gray-400">
              {t('membership.daysRemaining', {
                days: membership.daysRemaining,
              })}
            </p>
          )}
        </div>
      ) : (
        <div className="rounded-lg border bg-gray-50 p-4 text-center text-sm text-gray-500">
          {t('membership.renewReminder')}
        </div>
      )}

      {showPayNow && (
        <div className="rounded-lg border border-blue-200 bg-blue-50 p-4">
          <p className="mb-1 text-sm font-semibold text-blue-900">
            {membership
              ? `${isAr ? membership.planNameAr : membership.planName}`
              : t('membership.title')}
          </p>
          <p className="mb-3 text-xs text-blue-700">
            {t('payment.awaiting_payment')}
          </p>

          <button
            type="button"
            onClick={handlePayNow}
            disabled={payMutation.isPending}
            className="w-full rounded-md bg-blue-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {payMutation.isPending
              ? t('common.loading')
              : t('payment.pay_now')}
          </button>

          <p className="mt-2 text-center text-xs text-blue-600">
            {t('payment.methods_hint')}
          </p>

          {errorMessage && (
            <p className="mt-2 text-center text-xs text-red-600">
              {errorMessage}
            </p>
          )}
        </div>
      )}

      {!portalSettings?.onlinePaymentEnabled && isPayable && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-center text-sm text-amber-800">
          {t('payment.disabled_message')}
        </div>
      )}
    </div>
  )
}

function MembershipStatusBadge({ status }: { status: string }) {
  const { t } = useTranslation()

  const styles: Record<string, string> = {
    active: 'bg-green-100 text-green-800',
    frozen: 'bg-blue-100 text-blue-800',
    expired: 'bg-red-100 text-red-800',
    terminated: 'bg-gray-100 text-gray-800',
    pending: 'bg-yellow-100 text-yellow-800',
    pending_payment: 'bg-amber-100 text-amber-800',
    lapsed: 'bg-red-100 text-red-800',
  }

  return (
    <span
      className={`rounded-full px-2 py-0.5 text-xs font-medium ${styles[status] ?? 'bg-gray-100 text-gray-800'}`}
    >
      {t(`status.${status}`, status)}
    </span>
  )
}
