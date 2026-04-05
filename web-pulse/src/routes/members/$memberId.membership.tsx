import { createFileRoute } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  getActiveMembership,
  membershipKeys,
  unfreezeMembership,
  terminateMembership,
} from '@/api/memberships'
import { MembershipCard } from '@/components/membership/MembershipCard'
import { MembershipHistory } from '@/components/membership/MembershipHistory'
import { MembershipStatusTimeline } from '@/components/membership/MembershipStatusTimeline'
import { AssignPlanForm } from '@/components/membership/AssignPlanForm'
import { RenewalForm } from '@/components/membership/RenewalForm'
import { FreezeForm } from '@/components/membership/FreezeForm'
import { PermissionGate } from '@/components/shared/PermissionGate'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { Permission } from '@/types/permissions'
import type { ApiError } from '@/types/api'

export const Route = createFileRoute('/members/$memberId/membership')({
  component: MembershipTab,
})

type ActiveForm = 'assign' | 'renew' | 'freeze' | null

function MembershipTab() {
  const { memberId } = Route.useParams()
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [activeForm, setActiveForm] = useState<ActiveForm>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [terminateReason, setTerminateReason] = useState('')
  const [showTerminateConfirm, setShowTerminateConfirm] = useState(false)
  const [showUnfreezeConfirm, setShowUnfreezeConfirm] = useState(false)

  const { data: activeMembership, isLoading } = useQuery({
    queryKey: membershipKeys.active(memberId),
    queryFn: () => getActiveMembership(memberId),
    staleTime: 2 * 60 * 1000,
  })

  const showSuccess = (msg: string) => {
    setSuccessMessage(msg)
    setTimeout(() => setSuccessMessage(null), 5000)
  }

  const invalidateQueries = () => {
    queryClient.invalidateQueries({ queryKey: membershipKeys.active(memberId) })
    queryClient.invalidateQueries({ queryKey: membershipKeys.histories() })
    queryClient.invalidateQueries({ queryKey: ['members'] })
  }

  const unfreezeMutation = useMutation({
    mutationFn: () =>
      unfreezeMembership(memberId, activeMembership!.id),
    onSuccess: () => {
      invalidateQueries()
      setShowUnfreezeConfirm(false)
      showSuccess(t('membership.unfreezeSuccess'))
    },
  })

  const terminateMutation = useMutation({
    mutationFn: () =>
      terminateMembership(memberId, activeMembership!.id, {
        reason: terminateReason,
      }),
    onSuccess: () => {
      invalidateQueries()
      setShowTerminateConfirm(false)
      setTerminateReason('')
      showSuccess(t('membership.terminateSuccess'))
    },
  })

  if (isLoading) return <LoadingSkeleton rows={5} />

  const membership = activeMembership

  return (
    <div className="space-y-6">
      {successMessage && (
        <div className="rounded-md bg-green-50 p-3 text-sm text-green-700">
          {successMessage}
        </div>
      )}

      {membership ? (
        <>
          <MembershipCard membership={membership} />

          {/* Action buttons */}
          <div className="flex flex-wrap gap-3">
            <PermissionGate permission={Permission.MEMBERSHIP_CREATE}>
              {(membership.status === 'active' || membership.status === 'expired') && (
                <button
                  type="button"
                  onClick={() => setActiveForm('renew')}
                  className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700"
                >
                  {t('membership.renew')}
                </button>
              )}
            </PermissionGate>

            <PermissionGate permission={Permission.MEMBERSHIP_FREEZE}>
              {membership.status === 'active' && membership.plan.freezeAllowed && (
                <button
                  type="button"
                  onClick={() => setActiveForm('freeze')}
                  className="rounded-md bg-blue-100 px-4 py-2 text-sm font-medium text-blue-700 hover:bg-blue-200"
                >
                  {t('membership.freeze')}
                </button>
              )}
            </PermissionGate>

            <PermissionGate permission={Permission.MEMBERSHIP_UNFREEZE}>
              {membership.status === 'frozen' && (
                <button
                  type="button"
                  onClick={() => setShowUnfreezeConfirm(true)}
                  disabled={unfreezeMutation.isPending}
                  className="rounded-md bg-green-100 px-4 py-2 text-sm font-medium text-green-700 hover:bg-green-200 disabled:opacity-50"
                >
                  {unfreezeMutation.isPending
                    ? t('common.saving')
                    : t('membership.unfreeze')}
                </button>
              )}
            </PermissionGate>

            <PermissionGate permission={Permission.MEMBERSHIP_UPDATE}>
              {(membership.status === 'active' || membership.status === 'frozen') && (
                <button
                  type="button"
                  onClick={() => setShowTerminateConfirm(true)}
                  className="rounded-md bg-red-100 px-4 py-2 text-sm font-medium text-red-700 hover:bg-red-200"
                >
                  {t('membership.terminate')}
                </button>
              )}
            </PermissionGate>
          </div>

          {/* Unfreeze confirmation */}
          {showUnfreezeConfirm && (
            <div className="rounded-lg border border-gray-200 bg-white p-6">
              <p className="mb-4 text-sm text-gray-700">
                {t('membership.unfreezeConfirm')}
              </p>
              {unfreezeMutation.error && (
                <div className="mb-3 rounded-md bg-red-50 p-3 text-sm text-red-700">
                  {(unfreezeMutation.error as unknown as ApiError)?.detail ??
                    t('common.error')}
                </div>
              )}
              <div className="flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setShowUnfreezeConfirm(false)}
                  className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                >
                  {t('common.cancel')}
                </button>
                <button
                  type="button"
                  onClick={() => unfreezeMutation.mutate()}
                  disabled={unfreezeMutation.isPending}
                  className="rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-green-700 disabled:opacity-50"
                >
                  {unfreezeMutation.isPending
                    ? t('common.saving')
                    : t('membership.unfreeze')}
                </button>
              </div>
            </div>
          )}

          {/* Terminate confirmation */}
          {showTerminateConfirm && (
            <div className="rounded-lg border border-red-200 bg-white p-6">
              <p className="mb-4 text-sm font-medium text-red-700">
                {t('membership.terminateConfirm')}
              </p>
              <div className="mb-4">
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('membership.terminateReason')}
                </label>
                <textarea
                  value={terminateReason}
                  onChange={(e) => setTerminateReason(e.target.value)}
                  rows={2}
                  className="block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-red-500 focus:ring-1 focus:ring-red-500"
                />
              </div>
              {terminateMutation.error && (
                <div className="mb-3 rounded-md bg-red-50 p-3 text-sm text-red-700">
                  {(terminateMutation.error as unknown as ApiError)?.detail ??
                    t('common.error')}
                </div>
              )}
              <div className="flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => {
                    setShowTerminateConfirm(false)
                    setTerminateReason('')
                  }}
                  className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
                >
                  {t('common.cancel')}
                </button>
                <button
                  type="button"
                  onClick={() => terminateMutation.mutate()}
                  disabled={terminateMutation.isPending || !terminateReason.trim()}
                  className="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {terminateMutation.isPending
                    ? t('common.saving')
                    : t('membership.terminate')}
                </button>
              </div>
            </div>
          )}

          {/* Renew form */}
          {activeForm === 'renew' && (
            <div className="rounded-lg border border-gray-200 bg-white p-6">
              <h3 className="mb-4 text-lg font-semibold text-gray-900">
                {t('membership.renew')}
              </h3>
              <RenewalForm
                memberId={memberId}
                membershipId={membership.id}
                currentEndDate={membership.endDate}
                onSuccess={() => {
                  setActiveForm(null)
                  showSuccess(t('membership.renewSuccess'))
                }}
                onCancel={() => setActiveForm(null)}
              />
            </div>
          )}

          {/* Freeze form */}
          {activeForm === 'freeze' && (
            <div className="rounded-lg border border-gray-200 bg-white p-6">
              <h3 className="mb-4 text-lg font-semibold text-gray-900">
                {t('membership.freezeTitle')}
              </h3>
              <FreezeForm
                memberId={memberId}
                membershipId={membership.id}
                maxFreezeDays={membership.plan.maxFreezeDays}
                freezeDaysUsed={membership.freezeDaysUsed}
                onSuccess={() => {
                  setActiveForm(null)
                  showSuccess(t('membership.freezeSuccess'))
                }}
                onCancel={() => setActiveForm(null)}
              />
            </div>
          )}

          {/* Timeline */}
          <MembershipStatusTimeline membership={membership} />
        </>
      ) : (
        <div className="rounded-lg border border-dashed border-gray-300 px-6 py-12 text-center">
          <p className="mb-4 text-sm text-gray-500">{t('membership.noActive')}</p>
          <PermissionGate permission={Permission.MEMBERSHIP_CREATE}>
            <button
              type="button"
              onClick={() => setActiveForm('assign')}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700"
            >
              {t('membership.assignPlan')}
            </button>
          </PermissionGate>
        </div>
      )}

      {activeForm === 'assign' && (
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="mb-4 text-lg font-semibold text-gray-900">
            {t('membership.assignPlan')}
          </h3>
          <AssignPlanForm
            memberId={memberId}
            onSuccess={(invoiceNumber) => {
              setActiveForm(null)
              showSuccess(
                invoiceNumber
                  ? t('membership.invoiceCreated', { number: invoiceNumber })
                  : t('membership.success'),
              )
            }}
            onCancel={() => setActiveForm(null)}
          />
        </div>
      )}

      <div>
        <h3 className="mb-3 text-lg font-semibold text-gray-900">
          {t('membership.history')}
        </h3>
        <MembershipHistory memberId={memberId} />
      </div>
    </div>
  )
}
