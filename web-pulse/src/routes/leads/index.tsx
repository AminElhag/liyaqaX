import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getLeadList, leadKeys } from '@/api/leads'
import { getLeadSources, leadSourceKeys } from '@/api/leadSources'
import { PageShell } from '@/components/layout/PageShell'
import { LeadStageBadge } from '@/components/lead/LeadStageBadge'
import { LeadSourceBadge } from '@/components/lead/LeadSourceBadge'
import { LeadFilters } from '@/components/lead/LeadFilters'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { EmptyState } from '@/components/shared/EmptyState'
import { PermissionGate } from '@/components/shared/PermissionGate'
import { Permission } from '@/types/permissions'
import type { LeadSummary } from '@/types/domain'

export const Route = createFileRoute('/leads/')({
  component: LeadListPage,
})

function LeadListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [stage, setStage] = useState('')
  const [sourceId, setSourceId] = useState('')
  const [search, setSearch] = useState('')

  const { data: sources } = useQuery({
    queryKey: leadSourceKeys.list(),
    queryFn: getLeadSources,
    staleTime: 5 * 60 * 1000,
  })

  const listParams = {
    page,
    size: 20,
    ...(stage && { stage }),
    ...(sourceId && { leadSourceId: sourceId }),
    ...(search && { search }),
  }

  const { data, isLoading, isError } = useQuery({
    queryKey: leadKeys.list(listParams),
    queryFn: () => getLeadList(listParams),
    staleTime: 2 * 60 * 1000,
  })

  const actions = (
    <PermissionGate permission={Permission.LEAD_CREATE}>
      <button
        type="button"
        onClick={() => {/* New lead modal would open here */}}
        className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700"
      >
        {t('leads.new')}
      </button>
    </PermissionGate>
  )

  return (
    <PageShell title={t('leads.title')} actions={actions}>
      <div className="mb-4">
        <LeadFilters
          stage={stage}
          onStageChange={(v) => { setStage(v); setPage(0) }}
          sources={sources ?? []}
          sourceId={sourceId}
          onSourceChange={(v) => { setSourceId(v); setPage(0) }}
          search={search}
          onSearchChange={(v) => { setSearch(v); setPage(0) }}
        />
      </div>

      {isLoading && <LoadingSkeleton rows={8} />}

      {isError && (
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
          {t('common.error')}
        </div>
      )}

      {data && data.items.length === 0 && (
        <EmptyState message={t('leads.empty')} />
      )}

      {data && data.items.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-lg border border-gray-200">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('leads.name')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('leads.phone')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('leads.stage.label')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('leads.source')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('leads.assignedStaff')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 bg-white">
                {data.items.map((lead: LeadSummary) => (
                  <tr
                    key={lead.id}
                    className="cursor-pointer hover:bg-gray-50"
                    onClick={() => navigate({ to: '/leads/$leadId', params: { leadId: lead.id } })}
                  >
                    <td className="whitespace-nowrap px-4 py-3 text-sm font-medium text-gray-900">
                      {lead.firstName} {lead.lastName}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-500" dir="ltr">
                      {lead.phone ?? '-'}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm">
                      <LeadStageBadge stage={lead.stage} />
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm">
                      <LeadSourceBadge source={lead.leadSource} />
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-500">
                      {lead.assignedStaff
                        ? `${lead.assignedStaff.firstName} ${lead.assignedStaff.lastName}`
                        : '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {data.pagination.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between text-sm text-gray-500">
              <span>
                {t('common.page')} {data.pagination.page + 1} / {data.pagination.totalPages}
              </span>
              <div className="flex gap-2">
                <button
                  type="button"
                  disabled={data.pagination.page <= 0}
                  onClick={() => setPage((p) => p - 1)}
                  className="rounded border px-3 py-1 disabled:opacity-50"
                >
                  {t('common.previous')}
                </button>
                <button
                  type="button"
                  disabled={!data.pagination.hasNext}
                  onClick={() => setPage((p) => p + 1)}
                  className="rounded border px-3 py-1 disabled:opacity-50"
                >
                  {t('common.next')}
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </PageShell>
  )
}
