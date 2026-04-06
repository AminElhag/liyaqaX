import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useBranchStore } from '@/stores/useBranchStore'
import { ShortSurplusBadge } from '@/components/cash-drawer/ShortSurplusBadge'
import { getSessionList, cashDrawerKeys } from '@/api/cashDrawer'
import type { SessionListParams } from '@/api/cashDrawer'

export const Route = createFileRoute('/cash-drawer/history')({
  component: SessionHistoryPage,
})

function SessionHistoryPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const activeBranch = useBranchStore((s) => s.activeBranch)
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState('')

  const params: SessionListParams = {
    page,
    size: 20,
    sort: 'openedAt',
    order: 'desc',
    ...(statusFilter && { status: statusFilter }),
    ...(activeBranch?.id && { branchId: activeBranch.id }),
  }

  const { data, isLoading } = useQuery({
    queryKey: cashDrawerKeys.sessionList(params),
    queryFn: () => getSessionList(params),
  })

  const STATUS_STYLES: Record<string, string> = {
    open: 'bg-green-100 text-green-700',
    closed: 'bg-amber-100 text-amber-700',
    reconciled: 'bg-blue-100 text-blue-700',
  }

  const selectClass =
    'rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500'

  return (
    <div className="space-y-4 p-6">
      <h1 className="text-xl font-semibold text-gray-900">
        {t('cash_drawer.history')}
      </h1>

      {/* Filters */}
      <div className="flex gap-3">
        <select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value)
            setPage(0)
          }}
          className={selectClass}
        >
          <option value="">{t('cash_drawer.all_statuses')}</option>
          <option value="open">{t('cash_drawer.status_open')}</option>
          <option value="closed">{t('cash_drawer.status_closed')}</option>
          <option value="reconciled">{t('cash_drawer.status_reconciled')}</option>
        </select>
      </div>

      {isLoading && <div className="h-48 animate-pulse rounded-lg bg-gray-100" />}

      {data && (
        <>
          <div className="overflow-hidden rounded-lg border border-gray-200">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('cash_drawer.branch')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('cash_drawer.opened_at')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('cash_drawer.opened_by')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('common.status')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('cash_drawer.difference_label')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('cash_drawer.reconcile.status')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 bg-white">
                {data.items.map((session) => (
                  <tr
                    key={session.id}
                    className="cursor-pointer hover:bg-gray-50"
                    onClick={() =>
                      navigate({ to: '/cash-drawer/$sessionId', params: { sessionId: session.id } })
                    }
                  >
                    <td className="whitespace-nowrap px-4 py-3 text-sm">
                      {session.branch.name}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-500">
                      {new Date(session.openedAt).toLocaleString()}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-500">
                      {session.openedBy.firstName} {session.openedBy.lastName}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <span
                        className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                          STATUS_STYLES[session.status] ?? 'bg-gray-100 text-gray-700'
                        }`}
                      >
                        {session.status}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-4 py-3">
                      <ShortSurplusBadge difference={session.difference} />
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-500">
                      {session.reconciliationStatus ?? '—'}
                    </td>
                  </tr>
                ))}
                {data.items.length === 0 && (
                  <tr>
                    <td
                      colSpan={6}
                      className="py-8 text-center text-sm text-gray-500"
                    >
                      {t('cash_drawer.no_sessions')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {data.pagination.totalPages > 1 && (
            <div className="flex items-center justify-between">
              <p className="text-sm text-gray-500">
                {t('common.page')} {data.pagination.page + 1} / {data.pagination.totalPages}
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={data.pagination.page === 0}
                  className="rounded-md border px-3 py-1 text-sm disabled:opacity-50"
                >
                  {t('common.previous')}
                </button>
                <button
                  onClick={() => setPage((p) => p + 1)}
                  disabled={!data.pagination.hasNext}
                  className="rounded-md border px-3 py-1 text-sm disabled:opacity-50"
                >
                  {t('common.next')}
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
