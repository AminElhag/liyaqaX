import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  getClubSubscription,
  extendSubscription,
  cancelSubscription,
  assignSubscription,
  listPlans,
} from '@/api/subscriptions'
import { useState } from 'react'
import { useAuthStore } from '@/stores/useAuthStore'
import { hasPermission } from '@/lib/permissions'
import { Permission } from '@/types/permissions'
import { cn } from '@/lib/cn'

export const Route = createFileRoute('/subscriptions/$clubId')({
  component: ClubSubscriptionDetail,
})

function ClubSubscriptionDetail() {
  const { clubId } = Route.useParams()
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((s) => s.permissions)
  const canManage = hasPermission(permissions, Permission.SUBSCRIPTION_MANAGE)
  const [showAssign, setShowAssign] = useState(false)
  const [showExtend, setShowExtend] = useState(false)

  const { data: sub, isLoading, error } = useQuery({
    queryKey: ['club-subscription', clubId],
    queryFn: () => getClubSubscription(clubId),
    retry: false,
  })

  const extendMut = useMutation({
    mutationFn: (months: number) => extendSubscription(clubId, months),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['club-subscription', clubId] })
      setShowExtend(false)
    },
  })

  const cancelMut = useMutation({
    mutationFn: () => cancelSubscription(clubId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['club-subscription', clubId] }),
  })

  const noSub = !isLoading && (error || !sub)

  const statusColors: Record<string, string> = {
    ACTIVE: 'bg-green-100 text-green-700',
    GRACE: 'bg-orange-100 text-orange-700',
    EXPIRED: 'bg-red-100 text-red-700',
    CANCELLED: 'bg-gray-100 text-gray-500',
  }

  return (
    <div className="space-y-6 p-6">
      <h1 className="text-xl font-semibold text-gray-900">{t('subscription.page_title')}</h1>

      {isLoading && <p className="text-gray-500">{t('common.loading')}</p>}

      {noSub && (
        <div className="rounded-lg border bg-white p-6 text-center">
          <p className="mb-4 text-gray-500">No active subscription for this club.</p>
          {canManage && (
            <button
              type="button"
              onClick={() => setShowAssign(true)}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
            >
              {t('subscription.assign_title')}
            </button>
          )}
        </div>
      )}

      {sub && (
        <div className="rounded-lg border bg-white p-6 space-y-4">
          <div className="flex items-center gap-3">
            <h2 className="text-lg font-semibold">{sub.planName}</h2>
            <span className={cn('rounded-full px-2 py-0.5 text-xs font-medium', statusColors[sub.status] ?? 'bg-gray-100')}>
              {t(`subscription.status.${sub.status.toLowerCase()}`)}
            </span>
          </div>

          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <p className="text-gray-500">{t('subscription.table.price')}</p>
              <p className="font-medium">{sub.monthlyPriceSar} SAR/mo</p>
            </div>
            <div>
              <p className="text-gray-500">Period Start</p>
              <p className="font-medium">{new Date(sub.currentPeriodStart).toLocaleDateString()}</p>
            </div>
            <div>
              <p className="text-gray-500">{t('subscription.table.expires')}</p>
              <p className="font-medium">{new Date(sub.currentPeriodEnd).toLocaleDateString()}</p>
            </div>
            <div>
              <p className="text-gray-500">Grace Period Ends</p>
              <p className="font-medium">{new Date(sub.gracePeriodEndsAt).toLocaleDateString()}</p>
            </div>
          </div>

          {canManage && (sub.status === 'ACTIVE' || sub.status === 'GRACE') && (
            <div className="flex gap-3 border-t pt-4">
              <button
                type="button"
                onClick={() => setShowExtend(true)}
                className="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700"
              >
                {t('subscription.extend_title')}
              </button>
              <button
                type="button"
                onClick={() => { if (confirm(t('subscription.cancel_confirm'))) cancelMut.mutate() }}
                className="rounded-md border border-red-300 px-3 py-2 text-sm font-medium text-red-600 hover:bg-red-50"
              >
                {t('common.cancel')}
              </button>
            </div>
          )}
        </div>
      )}

      {showExtend && (
        <ExtendModal
          onSubmit={(months) => extendMut.mutate(months)}
          onClose={() => setShowExtend(false)}
          isLoading={extendMut.isPending}
        />
      )}

      {showAssign && (
        <AssignModal
          clubId={clubId}
          onClose={() => {
            setShowAssign(false)
            queryClient.invalidateQueries({ queryKey: ['club-subscription', clubId] })
          }}
        />
      )}
    </div>
  )
}

function ExtendModal({
  onSubmit,
  onClose,
  isLoading,
}: {
  onSubmit: (months: number) => void
  onClose: () => void
  isLoading: boolean
}) {
  const { t } = useTranslation()
  const [months, setMonths] = useState('1')

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="w-full max-w-sm rounded-lg bg-white p-6 space-y-4">
        <h2 className="text-lg font-semibold">{t('subscription.extend_title')}</h2>
        <div>
          <label className="block text-sm font-medium text-gray-700">{t('subscription.extend_months')}</label>
          <input type="number" min="1" value={months} onChange={(e) => setMonths(e.target.value)} className="mt-1 block w-full rounded-md border px-3 py-2 text-sm" />
        </div>
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onClose} className="rounded-md border px-3 py-2 text-sm">{t('common.cancel')}</button>
          <button type="button" onClick={() => onSubmit(parseInt(months, 10))} disabled={isLoading} className="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50">
            {t('common.save')}
          </button>
        </div>
      </div>
    </div>
  )
}

function AssignModal({ clubId, onClose }: { clubId: string; onClose: () => void }) {
  const { t } = useTranslation()
  const { data: plans = [] } = useQuery({
    queryKey: ['subscription-plans'],
    queryFn: listPlans,
  })

  const [planId, setPlanId] = useState('')
  const [startDate, setStartDate] = useState(new Date().toISOString().split('T')[0])
  const [months, setMonths] = useState('1')

  const assignMut = useMutation({
    mutationFn: () =>
      assignSubscription(clubId, {
        planPublicId: planId,
        periodStartDate: startDate,
        periodMonths: parseInt(months, 10),
      }),
    onSuccess: onClose,
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="w-full max-w-md rounded-lg bg-white p-6 space-y-4">
        <h2 className="text-lg font-semibold">{t('subscription.assign_title')}</h2>
        <div>
          <label className="block text-sm font-medium text-gray-700">{t('subscription.assign_plan')}</label>
          <select value={planId} onChange={(e) => setPlanId(e.target.value)} className="mt-1 block w-full rounded-md border px-3 py-2 text-sm">
            <option value="">Select a plan...</option>
            {plans.map((p) => (
              <option key={p.id} value={p.id}>{p.name} ({p.monthlyPriceSar} SAR/mo)</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700">{t('subscription.assign_period_start')}</label>
          <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} className="mt-1 block w-full rounded-md border px-3 py-2 text-sm" />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700">{t('subscription.assign_months')}</label>
          <input type="number" min="1" value={months} onChange={(e) => setMonths(e.target.value)} className="mt-1 block w-full rounded-md border px-3 py-2 text-sm" />
        </div>
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onClose} className="rounded-md border px-3 py-2 text-sm">{t('common.cancel')}</button>
          <button type="button" onClick={() => assignMut.mutate()} disabled={!planId || assignMut.isPending} className="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50">
            {t('common.save')}
          </button>
        </div>
      </div>
    </div>
  )
}
