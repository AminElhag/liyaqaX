import { createFileRoute } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  getMembershipPlanList,
  getMembershipPlan,
  createMembershipPlan,
  updateMembershipPlan,
  membershipPlanKeys,
} from '@/api/membershipPlans'
import { PageShell } from '@/components/layout/PageShell'
import { PlanCard } from '@/components/membership/PlanCard'
import { PlanForm } from '@/components/membership/PlanForm'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { PermissionGate } from '@/components/shared/PermissionGate'
import { Permission } from '@/types/permissions'
import type {
  MembershipPlanSummary,
  CreateMembershipPlanRequest,
  UpdateMembershipPlanRequest,
} from '@/types/domain'
import type { ApiError } from '@/types/api'

export const Route = createFileRoute('/memberships/plans')({
  component: PlansPage,
})

type FormMode =
  | { type: 'closed' }
  | { type: 'create' }
  | { type: 'edit'; planId: string }

function PlansPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [formMode, setFormMode] = useState<FormMode>({ type: 'closed' })

  const {
    data,
    isLoading,
    isError,
  } = useQuery({
    queryKey: membershipPlanKeys.list({ sort: 'sortOrder', order: 'asc', size: 100 }),
    queryFn: () => getMembershipPlanList({ sort: 'sortOrder', order: 'asc', size: 100 }),
    staleTime: 2 * 60 * 1000,
  })

  const editPlanQuery = useQuery({
    queryKey: membershipPlanKeys.detail(
      formMode.type === 'edit' ? formMode.planId : '',
    ),
    queryFn: () => getMembershipPlan(formMode.type === 'edit' ? formMode.planId : ''),
    enabled: formMode.type === 'edit',
  })

  const createMutation = useMutation({
    mutationFn: createMembershipPlan,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: membershipPlanKeys.all })
      setFormMode({ type: 'closed' })
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateMembershipPlanRequest }) =>
      updateMembershipPlan(id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: membershipPlanKeys.all })
      setFormMode({ type: 'closed' })
    },
  })

  const activeMutation = formMode.type === 'create' ? createMutation : updateMutation
  const mutationError = activeMutation.error as ApiError | null

  function handleEdit(plan: MembershipPlanSummary) {
    setFormMode({ type: 'edit', planId: plan.id })
  }

  function handleFormSubmit(
    request: CreateMembershipPlanRequest | UpdateMembershipPlanRequest,
  ) {
    if (formMode.type === 'create') {
      createMutation.mutate(request as CreateMembershipPlanRequest)
    } else if (formMode.type === 'edit') {
      updateMutation.mutate({
        id: formMode.planId,
        request: request as UpdateMembershipPlanRequest,
      })
    }
  }

  const actions = (
    <PermissionGate permission={Permission.MEMBERSHIP_PLAN_CREATE}>
      <button
        type="button"
        onClick={() => setFormMode({ type: 'create' })}
        className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700"
      >
        {t('membershipPlans.addPlan')}
      </button>
    </PermissionGate>
  )

  return (
    <PageShell title={t('membershipPlans.title')} actions={actions}>
      {mutationError && (
        <div className="mb-4 rounded-md bg-red-50 p-4 text-sm text-red-700">
          {mutationError.detail ?? t('common.error')}
        </div>
      )}

      {formMode.type !== 'closed' && (
        <div className="mb-6 rounded-lg border border-gray-200 bg-white p-6">
          <h2 className="mb-4 text-lg font-semibold text-gray-900">
            {formMode.type === 'create'
              ? t('membershipPlans.addPlan')
              : t('membershipPlans.editPlan')}
          </h2>
          {formMode.type === 'edit' && editPlanQuery.isLoading ? (
            <LoadingSkeleton rows={4} />
          ) : (
            <PlanForm
              plan={
                formMode.type === 'edit'
                  ? editPlanQuery.data
                  : undefined
              }
              onSubmit={handleFormSubmit}
              onCancel={() => setFormMode({ type: 'closed' })}
              isSubmitting={activeMutation.isPending}
            />
          )}
        </div>
      )}

      {isLoading && <LoadingSkeleton rows={6} />}

      {isError && (
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
          {t('common.error')}
        </div>
      )}

      {data && data.items.length === 0 && (
        <div className="rounded-md bg-gray-50 p-8 text-center text-sm text-gray-500">
          {t('membershipPlans.empty')}
        </div>
      )}

      {data && data.items.length > 0 && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.items.map((plan) => (
            <PlanCard key={plan.id} plan={plan} onEdit={handleEdit} />
          ))}
        </div>
      )}
    </PageShell>
  )
}
