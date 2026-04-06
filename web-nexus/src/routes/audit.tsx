import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getAuditLog } from '@/api/audit'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { EmptyState } from '@/components/common/EmptyState'
import { Pagination } from '@/components/common/Pagination'

export const Route = createFileRoute('/audit')({
  component: AuditPage,
})

function AuditPage() {
  const { t, i18n } = useTranslation()
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['audit-log', page],
    queryFn: () => getAuditLog({ page, size: 20 }),
    staleTime: 30_000,
  })

  return (
    <div className="p-6">
      <h2 className="mb-6 text-xl font-semibold text-gray-900">
        {t('audit.title')}
      </h2>

      {data?.meta?.note && (
        <div className="mb-4 rounded-md bg-blue-50 p-3 text-sm text-blue-700">
          {t('audit.empty_note')}
        </div>
      )}

      {isLoading && <LoadingSpinner />}

      {data && data.items.length === 0 && (
        <EmptyState message={t('audit.empty_note')} />
      )}

      {data && data.items.length > 0 && (
        <>
          <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('common.created_at')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('members.email')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('common.actions')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    Entity
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {data.items.map((entry) => (
                  <tr key={entry.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 text-sm text-gray-500">
                      {new Intl.DateTimeFormat(i18n.language, {
                        dateStyle: 'medium',
                        timeStyle: 'short',
                        timeZone: 'Asia/Riyadh',
                      }).format(new Date(entry.createdAt))}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {entry.actorEmail}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-900">
                      {entry.action}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {entry.entityType} / {entry.entityId}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <Pagination
            page={page}
            totalPages={data.pagination.totalPages}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  )
}
