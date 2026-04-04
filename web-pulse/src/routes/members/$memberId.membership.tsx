import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getActiveMembership, membershipKeys } from '@/api/memberships'
import { MembershipCard } from '@/components/membership/MembershipCard'
import { MembershipHistory } from '@/components/membership/MembershipHistory'
import { AssignPlanForm } from '@/components/membership/AssignPlanForm'
import { PermissionGate } from '@/components/shared/PermissionGate'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { Permission } from '@/types/permissions'

export const Route = createFileRoute('/members/$memberId/membership')({
  component: MembershipTab,
})

function MembershipTab() {
  const { memberId } = Route.useParams()
  const { t } = useTranslation()
  const [showAssignForm, setShowAssignForm] = useState(false)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const { data: activeMembership, isLoading } = useQuery({
    queryKey: membershipKeys.active(memberId),
    queryFn: () => getActiveMembership(memberId),
    staleTime: 2 * 60 * 1000,
  })

  if (isLoading) return <LoadingSkeleton rows={5} />

  return (
    <div className="space-y-6">
      {successMessage && (
        <div className="rounded-md bg-green-50 p-3 text-sm text-green-700">
          {successMessage}
        </div>
      )}

      {activeMembership ? (
        <MembershipCard membership={activeMembership} />
      ) : (
        <div className="rounded-lg border border-dashed border-gray-300 px-6 py-12 text-center">
          <p className="mb-4 text-sm text-gray-500">{t('membership.noActive')}</p>
          <PermissionGate permission={Permission.MEMBERSHIP_CREATE}>
            <button
              type="button"
              onClick={() => setShowAssignForm(true)}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700"
            >
              {t('membership.assignPlan')}
            </button>
          </PermissionGate>
        </div>
      )}

      {showAssignForm && (
        <div className="rounded-lg border border-gray-200 bg-white p-6">
          <h3 className="mb-4 text-lg font-semibold text-gray-900">
            {t('membership.assignPlan')}
          </h3>
          <AssignPlanForm
            memberId={memberId}
            onSuccess={(invoiceNumber) => {
              setShowAssignForm(false)
              setSuccessMessage(
                invoiceNumber
                  ? t('membership.invoiceCreated', { number: invoiceNumber })
                  : t('membership.success'),
              )
              setTimeout(() => setSuccessMessage(null), 5000)
            }}
            onCancel={() => setShowAssignForm(false)}
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
