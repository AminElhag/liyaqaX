<<<<<<< Updated upstream
import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getStaffMember, staffKeys } from '@/api/staff'
import { PageShell } from '@/components/layout/PageShell'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { StaffStatusBadge } from '@/components/staff/StaffStatusBadge'
import { formatDate } from '@/lib/formatDate'

export const Route = createFileRoute('/staff/$staffId')({
  component: StaffDetailPage,
})

function StaffDetailPage() {
  const { staffId } = Route.useParams()
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'

  const { data: staff, isLoading, isError } = useQuery({
    queryKey: staffKeys.detail(staffId),
    queryFn: () => getStaffMember(staffId),
    staleTime: 2 * 60 * 1000,
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
          <Row label={t('staff.columns.role')} value={roleName} />
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
=======
import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/staff/$staffId')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/staff/$staffId"!</div>
>>>>>>> Stashed changes
}
