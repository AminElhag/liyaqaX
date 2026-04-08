import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { getPaymentStatus, paymentKeys } from '@/api/payments'

export const Route = createFileRoute('/payment-callback/')({
  component: PaymentCallbackPage,
})

function PaymentCallbackPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const params = new URLSearchParams(window.location.search)
  const moyasarId = params.get('id') ?? ''

  const { data, isLoading, isError } = useQuery({
    queryKey: paymentKeys.status(moyasarId),
    queryFn: () => getPaymentStatus(moyasarId),
    enabled: !!moyasarId,
    staleTime: 0,
  })

  useEffect(() => {
    if (data?.status === 'PAID') {
      const timer = setTimeout(() => {
        navigate({ to: '/membership' })
      }, 2000)
      return () => clearTimeout(timer)
    }
  }, [data?.status, navigate])

  if (!moyasarId) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center p-4">
        <div className="text-center">
          <p className="text-sm text-gray-500">{t('common.error')}</p>
        </div>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center p-4">
        <div className="text-center">
          <div className="mx-auto mb-4 h-8 w-8 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" />
          <p className="text-sm text-gray-500">{t('common.loading')}</p>
        </div>
      </div>
    )
  }

  if (isError || !data) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center p-4">
        <div className="text-center">
          <p className="mb-4 text-sm text-red-600">{t('common.error')}</p>
          <button
            type="button"
            onClick={() => navigate({ to: '/membership' })}
            className="text-sm text-blue-600 hover:text-blue-800"
          >
            {t('payment.callback_retry_link')}
          </button>
        </div>
      </div>
    )
  }

  if (data.status === 'PAID') {
    return (
      <div className="flex min-h-[60vh] items-center justify-center p-4">
        <div className="text-center">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-green-100">
            <svg
              className="h-8 w-8 text-green-600"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M5 13l4 4L19 7"
              />
            </svg>
          </div>
          <h2 className="mb-1 text-lg font-semibold text-green-800">
            {t('payment.callback_success')}
          </h2>
          <p className="text-sm text-gray-500">
            {t('payment.callback_activating')}
          </p>
        </div>
      </div>
    )
  }

  // failed or cancelled
  return (
    <div className="flex min-h-[60vh] items-center justify-center p-4">
      <div className="text-center">
        <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-red-100">
          <svg
            className="h-8 w-8 text-red-600"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M6 18L18 6M6 6l12 12"
            />
          </svg>
        </div>
        <h2 className="mb-1 text-lg font-semibold text-red-800">
          {t('payment.callback_failed')}
        </h2>
        <button
          type="button"
          onClick={() => navigate({ to: '/membership' })}
          className="mt-3 text-sm text-blue-600 hover:text-blue-800"
        >
          {t('payment.callback_retry_link')}
        </button>
      </div>
    </div>
  )
}
