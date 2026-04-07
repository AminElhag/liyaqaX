import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { PageShell } from '@/components/layout/PageShell'
import { PermissionGate } from '@/components/shared/PermissionGate'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { EmptyState } from '@/components/shared/EmptyState'
import { Permission } from '@/types/permissions'
import { listTemplates, reportBuilderKeys } from '@/api/reportBuilder'

export const Route = createFileRoute('/reports/builder/')({
  component: BuilderIndexPage,
})

function BuilderIndexPage() {
  const { t } = useTranslation()

  const { data: templates, isLoading } = useQuery({
    queryKey: reportBuilderKeys.templates(),
    queryFn: listTemplates,
    staleTime: 5 * 60 * 1000,
  })

  return (
    <PermissionGate permission={Permission.REPORT_CUSTOM_RUN}>
      <PageShell
        title={t('reports.builder.title')}
        actions={
          <Link
            to="/reports/builder/new"
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            {t('reports.builder.newReport')}
          </Link>
        }
      >
        {isLoading && <LoadingSkeleton rows={4} />}

        {templates && templates.length === 0 && (
          <EmptyState message={t('reports.builder.emptyDescription')} />
        )}

        {templates && templates.length > 0 && (
          <div className="overflow-x-auto rounded-lg border border-gray-200">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('reports.builder.name')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('reports.builder.metrics')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('reports.builder.dimensions')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('reports.builder.lastRun')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('reports.builder.actions')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 bg-white">
                {templates.map((tpl) => (
                  <tr key={tpl.id}>
                    <td className="whitespace-nowrap px-4 py-3 text-sm font-medium text-gray-900">
                      <Link
                        to="/reports/builder/$templateId"
                        params={{ templateId: tpl.id }}
                        className="text-blue-600 hover:underline"
                      >
                        {tpl.name}
                      </Link>
                      {tpl.isSystem && (
                        <span className="ms-2 inline-flex rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600">
                          {t('reports.builder.system')}
                        </span>
                      )}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-700">
                      {tpl.metrics.length}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-700">
                      {tpl.dimensions.join(', ')}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-500">
                      {tpl.lastRunAt
                        ? new Date(tpl.lastRunAt).toLocaleDateString()
                        : t('reports.builder.neverRun')}
                    </td>
                    <td className="whitespace-nowrap px-4 py-3 text-sm">
                      <Link
                        to="/reports/builder/$templateId"
                        params={{ templateId: tpl.id }}
                        className="text-sm font-medium text-blue-600 hover:underline"
                      >
                        {t('reports.builder.view')}
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </PermissionGate>
  )
}
