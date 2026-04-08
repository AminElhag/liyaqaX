import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getStaffMember, staffKeys } from '@/api/staff'
import { listClubRoles, assignStaffRole, roleKeys } from '@/api/roles'
import { PageShell } from '@/components/layout/PageShell'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { PermissionGate } from '@/components/shared/PermissionGate'
import { StaffStatusBadge } from '@/components/staff/StaffStatusBadge'
import { Permission } from '@/types/permissions'
import { formatDate } from '@/lib/formatDate'

export const Route = createFileRoute('/staff/$staffId')({
  component: StaffDetailPage,
})

function StaffDetailPage() {
  const { staffId } = Route.useParams()
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const isAr = i18n.language === 'ar'
  const [changingRole, setChangingRole] = useState(false)
  const [selectedRoleId, setSelectedRoleId] = useState('')

  const { data: staff, isLoading, isError } = useQuery({
    queryKey: staffKeys.detail(staffId),
    queryFn: () => getStaffMember(staffId),
    staleTime: 2 * 60 * 1000,
  })

  const { data: clubRoles } = useQuery({
    queryKey: roleKeys.list(),
    queryFn: listClubRoles,
    staleTime: 120_000,
    enabled: changingRole,
  })

  const assignRoleMutation = useMutation({
    mutationFn: (roleId: string) => assignStaffRole(staffId, roleId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: staffKeys.detail(staffId) })
      setChangingRole(false)
      setSelectedRoleId('')
    },
  })

  if (isLoading) {
    return (
      <PageShell title={t('staff.detail.title')}>
        <LoadingSkeleton rows={6} />
      </PageShell>
    )
  }

  if (isError || !staff) {
    return (
      <PageShell title={t('staff.detail.title')}>
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
          {t('staff.detail.notFound')}
        </div>
      </PageShell>
    )
  }

  const fullName = isAr
    ? `${staff.firstNameAr} ${staff.lastNameAr}`
    : `${staff.firstNameEn} ${staff.lastNameEn}`

  const roleName = isAr ? staff.role.nameAr : staff.role.nameEn

  return (
    <PageShell
      title={fullName}
      actions={
        <Link
          to="/staff"
          className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
        >
          {t('common.goBack')}
        </Link>
      }
    >
      <div className="rounded-lg border border-gray-200 bg-white">
        <dl className="divide-y divide-gray-100">
          <Row label={t('staff.columns.email')} value={staff.email} />
          <Row
            label={t('staff.columns.role')}
            value={
              <div className="flex items-center gap-2">
                <span>{roleName}</span>
                <PermissionGate permission={Permission.STAFF_UPDATE}>
                  {changingRole ? (
                    <div className="flex items-center gap-2">
                      <select
                        value={selectedRoleId}
                        onChange={(e) => setSelectedRoleId(e.target.value)}
                        className="rounded-md border border-gray-300 px-2 py-1 text-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500 focus:outline-none"
                      >
                        <option value="">{t('staff.detail.selectRole')}</option>
                        {clubRoles?.map((r) => (
                          <option key={r.id} value={r.id}>
                            {isAr ? r.nameAr : r.nameEn}
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        onClick={() => {
                          if (
                            selectedRoleId &&
                            window.confirm(t('staff.detail.changeRoleConfirm'))
                          ) {
                            assignRoleMutation.mutate(selectedRoleId)
                          }
                        }}
                        disabled={!selectedRoleId || assignRoleMutation.isPending}
                        className="rounded-md bg-blue-600 px-2 py-1 text-xs text-white hover:bg-blue-700 disabled:opacity-50"
                      >
                        {t('staff.detail.confirmRole')}
                      </button>
                      <button
                        type="button"
                        onClick={() => setChangingRole(false)}
                        className="text-xs text-gray-500 hover:text-gray-700"
                      >
                        {t('common.cancel')}
                      </button>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setChangingRole(true)}
                      className="text-xs text-blue-600 hover:text-blue-800"
                    >
                      {t('staff.detail.changeRole')}
                    </button>
                  )}
                </PermissionGate>
              </div>
            }
          />
          <Row
            label={t('staff.columns.status')}
            value={<StaffStatusBadge isActive={staff.isActive} />}
          />
          {staff.phone && (
            <Row label={t('staff.detail.phone')} value={staff.phone} />
          )}
          {staff.nationalId && (
            <Row label={t('staff.detail.nationalId')} value={staff.nationalId} />
          )}
          <Row
            label={t('staff.detail.employmentType')}
            value={staff.employmentType}
          />
          <Row
            label={t('staff.detail.joinedAt')}
            value={formatDate(staff.joinedAt, i18n.language)}
          />
          {staff.branches.length > 0 && (
            <Row
              label={t('staff.detail.branches')}
              value={
                <div className="flex flex-wrap gap-1">
                  {staff.branches.map((b) => (
                    <span
                      key={b.id}
                      className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-700"
                    >
                      {isAr ? b.nameAr : b.nameEn}
                    </span>
                  ))}
                </div>
              }
            />
          )}
        </dl>
      </div>
    </PageShell>
  )
}

function Row({
  label,
  value,
}: {
  label: string
  value: React.ReactNode
}) {
  return (
    <div className="flex items-start px-6 py-4">
      <dt className="w-40 shrink-0 text-sm font-medium text-gray-500">
        {label}
      </dt>
      <dd className="text-sm text-gray-900">{value}</dd>
    </div>
  )
}
