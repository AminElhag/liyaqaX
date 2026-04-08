import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getMemberList, memberKeys } from '@/api/members'
import { PageShell } from '@/components/layout/PageShell'
import { MemberTable } from '@/components/members/MemberTable'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { PermissionGate } from '@/components/shared/PermissionGate'
import { Permission } from '@/types/permissions'

export const Route = createFileRoute('/members/')({
  component: MemberListPage,
})

function MemberListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [page, setPage] = useState(0)

  const { data, isLoading, isError } = useQuery({
    queryKey: memberKeys.list({ page, size: 20 }),
    queryFn: () => getMemberList({ page, size: 20 }),
    staleTime: 2 * 60 * 1000,
  })

  const actions = (
    <PermissionGate permission={Permission.MEMBER_CREATE}>
      <button
        type="button"
        onClick={() => navigate({ to: '/members/new' })}
        className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700"
      >
        {t('members.registerMember')}
      </button>
    </PermissionGate>
  )

  return (
    <PageShell title={t('members.title')} actions={actions}>
      {isLoading && <LoadingSkeleton rows={8} />}

      {isError && (
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
          {t('common.error')}
        </div>
      )}

      {data && (
        <MemberTable
          members={data.items}
          pagination={data.pagination}
          onPageChange={setPage}
        />
      )}
    </PageShell>
  )
}
