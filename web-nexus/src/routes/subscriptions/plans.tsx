import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { listPlans, createPlan, updatePlan, deletePlan } from '@/api/subscriptions'
import type { SubscriptionPlanResponse } from '@/api/subscriptions'
import { useState } from 'react'
import { useAuthStore } from '@/stores/useAuthStore'
import { hasPermission } from '@/lib/permissions'
import { Permission } from '@/types/permissions'

export const Route = createFileRoute('/subscriptions/plans')({
  component: PlanCatalog,
})

function PlanCatalog() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((s) => s.permissions)
  const canManage = hasPermission(permissions, Permission.SUBSCRIPTION_MANAGE)
  const [showCreate, setShowCreate] = useState(false)
  const [editingPlan, setEditingPlan] = useState<SubscriptionPlanResponse | null>(null)

  const { data: plans = [], isLoading } = useQuery({
    queryKey: ['subscription-plans'],
    queryFn: listPlans,
  })

  const createMutation = useMutation({
    mutationFn: createPlan,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['subscription-plans'] })
      setShowCreate(false)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, ...payload }: { id: string; name: string; monthlyPriceHalalas: number; maxBranches: number; maxStaff: number }) =>
      updatePlan(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['subscription-plans'] })
      setEditingPlan(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deletePlan,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['subscription-plans'] }),
  })

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-gray-900">{t('subscription.plan.catalog_title')}</h1>
        {canManage && (
          <button
            type="button"
            onClick={() => setShowCreate(true)}
            className="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            {t('subscription.plan.create')}
          </button>
        )}
      </div>

      {isLoading ? (
        <p className="text-gray-500">{t('common.loading')}</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {plans.map((plan) => (
            <div key={plan.id} className="rounded-lg border bg-white p-5 space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-lg font-semibold text-gray-900">{plan.name}</h3>
                {plan.isActive && (
                  <span className="rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700">Active</span>
                )}
              </div>
              <p className="text-2xl font-bold text-gray-900">{plan.monthlyPriceSar} <span className="text-sm font-normal text-gray-500">SAR/mo</span></p>
              <div className="space-y-1 text-sm text-gray-600">
                <p>{t('subscription.plan.max_branches')}: {plan.maxBranches === 0 ? t('subscription.plan.unlimited') : plan.maxBranches}</p>
                <p>{t('subscription.plan.max_staff')}: {plan.maxStaff === 0 ? t('subscription.plan.unlimited') : plan.maxStaff}</p>
              </div>
              {canManage && (
                <div className="flex gap-2 border-t pt-3">
                  <button
                    type="button"
                    onClick={() => setEditingPlan(plan)}
                    className="text-sm font-medium text-blue-600 hover:text-blue-700"
                  >
                    {t('common.edit')}
                  </button>
                  <button
                    type="button"
                    onClick={() => { if (confirm('Delete this plan?')) deleteMutation.mutate(plan.id) }}
                    className="text-sm font-medium text-red-600 hover:text-red-700"
                  >
                    Delete
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {(showCreate || editingPlan) && (
        <PlanFormModal
          plan={editingPlan}
          onSubmit={(data) => {
            if (editingPlan) {
              updateMutation.mutate({ id: editingPlan.id, ...data })
            } else {
              createMutation.mutate(data)
            }
          }}
          onClose={() => { setShowCreate(false); setEditingPlan(null) }}
          isLoading={createMutation.isPending || updateMutation.isPending}
        />
      )}
    </div>
  )
}

function PlanFormModal({
  plan,
  onSubmit,
  onClose,
  isLoading,
}: {
  plan: SubscriptionPlanResponse | null
  onSubmit: (data: { name: string; monthlyPriceHalalas: number; maxBranches: number; maxStaff: number }) => void
  onClose: () => void
  isLoading: boolean
}) {
  const { t } = useTranslation()
  const [name, setName] = useState(plan?.name ?? '')
  const [priceSar, setPriceSar] = useState(plan ? (plan.monthlyPriceHalalas / 100).toString() : '')
  const [maxBranches, setMaxBranches] = useState(plan?.maxBranches.toString() ?? '0')
  const [maxStaff, setMaxStaff] = useState(plan?.maxStaff.toString() ?? '0')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onSubmit({
      name,
      monthlyPriceHalalas: Math.round(parseFloat(priceSar) * 100),
      maxBranches: parseInt(maxBranches, 10),
      maxStaff: parseInt(maxStaff, 10),
    })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <form onSubmit={handleSubmit} className="w-full max-w-md rounded-lg bg-white p-6 space-y-4">
        <h2 className="text-lg font-semibold">{plan ? t('common.edit') : t('subscription.plan.create')}</h2>
        <div>
          <label className="block text-sm font-medium text-gray-700">{t('subscription.plan.name')}</label>
          <input type="text" value={name} onChange={(e) => setName(e.target.value)} required className="mt-1 block w-full rounded-md border px-3 py-2 text-sm" />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700">{t('subscription.plan.price')} (SAR)</label>
          <input type="number" step="0.01" value={priceSar} onChange={(e) => setPriceSar(e.target.value)} required className="mt-1 block w-full rounded-md border px-3 py-2 text-sm" />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700">{t('subscription.plan.max_branches')}</label>
            <input type="number" min="0" value={maxBranches} onChange={(e) => setMaxBranches(e.target.value)} className="mt-1 block w-full rounded-md border px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">{t('subscription.plan.max_staff')}</label>
            <input type="number" min="0" value={maxStaff} onChange={(e) => setMaxStaff(e.target.value)} className="mt-1 block w-full rounded-md border px-3 py-2 text-sm" />
          </div>
        </div>
        <p className="text-xs text-gray-500">0 = {t('subscription.plan.unlimited')}</p>
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onClose} className="rounded-md border px-3 py-2 text-sm">{t('common.cancel')}</button>
          <button type="submit" disabled={isLoading} className="rounded-md bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50">
            {t('common.save')}
          </button>
        </div>
      </form>
    </div>
  )
}
