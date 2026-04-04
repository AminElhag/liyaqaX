import { createFileRoute } from '@tanstack/react-router'
<<<<<<< Updated upstream
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getStaffList, staffKeys } from '@/api/staff'
import { PageShell } from '@/components/layout/PageShell'
import { StaffTable } from '@/components/staff/StaffTable'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { PermissionGate } from '@/components/shared/PermissionGate'
import { Permission } from '@/types/permissions'

export const Route = createFileRoute('/staff/')({
  component: StaffListPage,
})

function StaffListPage() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)

  const { data, isLoading, isError } = useQuery({
    queryKey: staffKeys.list({ page, size: 20 }),
    queryFn: () => getStaffList({ page, size: 20 }),
    staleTime: 2 * 60 * 1000,
  })

  const actions = (
    <PermissionGate permission={Permission.STAFF_CREATE}>
      <button
        type="button"
        disabled
        title="Coming soon"
        className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {t('staff.addStaff')}
      </button>
    </PermissionGate>
  )

  return (
    <PageShell title={t('staff.title')} actions={actions}>
      {isLoading && <LoadingSkeleton rows={8} />}

      {isError && (
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
          {t('common.error')}
        </div>
      )}

      {data && (
        <StaffTable
          staff={data.items}
          pagination={data.pagination}
          onPageChange={setPage}
        />
      )}
    </PageShell>
  )
=======

export const Route = createFileRoute('/staff/')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/staff/"!</div>
>>>>>>> Stashed changes
}
