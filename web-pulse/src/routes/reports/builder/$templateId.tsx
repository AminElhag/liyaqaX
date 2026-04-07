import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { PageShell } from '@/components/layout/PageShell'
import { ReportPreviewTable } from '@/components/reportBuilder/ReportPreviewTable'
import { ReportDateRangePicker } from '@/components/reports/ReportDateRangePicker'
import { ExportCsvButton } from '@/components/reports/ExportCsvButton'
import { KpiCard } from '@/components/reports/KpiCard'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import {
  getTemplate,
  runReport,
  deleteTemplate,
  exportCsvUrl,
  reportBuilderKeys,
} from '@/api/reportBuilder'
import type { ReportResultResponse } from '@/api/reportBuilder'

export const Route = createFileRoute('/reports/builder/$templateId')({
  component: TemplateDetailPage,
})

function defaultFrom(): string {
  const d = new Date()
  d.setDate(1)
  return d.toISOString().slice(0, 10)
}

function defaultTo(): string {
  return new Date().toISOString().slice(0, 10)
}

function TemplateDetailPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { templateId } = Route.useParams()

  const [dateFrom, setDateFrom] = useState(defaultFrom)
  const [dateTo, setDateTo] = useState(defaultTo)
  const [result, setResult] = useState<ReportResultResponse | null>(null)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  const { data: template, isLoading: loadingTemplate } = useQuery({
    queryKey: reportBuilderKeys.template(templateId),
    queryFn: () => getTemplate(templateId),
    staleTime: 5 * 60 * 1000,
  })

  const runMutation = useMutation({
    mutationFn: () =>
      runReport(templateId, {
        dateFrom,
        dateTo,
      }),
    onSuccess: (data) => setResult(data),
  })

  const deleteMutation = useMutation({
    mutationFn: () => deleteTemplate(templateId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: reportBuilderKeys.templates() })
      navigate({ to: '/reports/builder' })
    },
  })

  const dateRangeDays = Math.abs(
    (new Date(dateTo).getTime() - new Date(dateFrom).getTime()) / (1000 * 60 * 60 * 24),
  )
  const dateRangeValid = dateRangeDays <= 366 && dateFrom <= dateTo

  if (loadingTemplate) {
    return (
      <PageShell title="...">
        <LoadingSkeleton rows={4} />
      </PageShell>
    )
  }

  if (!template) return null

  return (
    <PageShell
      title={template.name}
      actions={
        <div className="flex gap-2">
          <ExportCsvButton href={exportCsvUrl(templateId)} />
          {!template.isSystem && (
            <button
              type="button"
              onClick={() => setShowDeleteConfirm(true)}
              className="rounded-md border border-red-300 bg-white px-3 py-1.5 text-sm font-medium text-red-700 hover:bg-red-50"
            >
              {t('reports.builder.deleteTemplate')}
            </button>
          )}
        </div>
      }
    >
      <div className="space-y-6">
        {template.description && (
          <p className="text-sm text-gray-500">{template.description}</p>
        )}

        <div className="flex flex-wrap gap-2">
          {template.metrics.map((code) => (
            <span
              key={code}
              className="inline-flex rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-medium text-blue-700"
            >
              {code}
            </span>
          ))}
          {template.dimensions.map((code) => (
            <span
              key={code}
              className="inline-flex rounded-full bg-green-50 px-2.5 py-0.5 text-xs font-medium text-green-700"
            >
              {code}
            </span>
          ))}
        </div>

        <div className="rounded-lg border border-gray-200 bg-gray-50 p-4">
          <div className="flex flex-wrap items-end gap-4">
            <ReportDateRangePicker
              from={dateFrom}
              to={dateTo}
              onFromChange={setDateFrom}
              onToChange={setDateTo}
            />
            <button
              type="button"
              onClick={() => runMutation.mutate()}
              disabled={!dateRangeValid || runMutation.isPending}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {runMutation.isPending
                ? t('reports.builder.running')
                : t('reports.builder.runReport')}
            </button>
          </div>
          {!dateRangeValid && (
            <p className="mt-2 text-xs text-red-600">{t('reports.builder.maxDateRange')}</p>
          )}
        </div>

        {result && (
          <>
            <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
              <KpiCard label="Rows" value={result.rowCount} />
              <KpiCard label="Date Range" value={`${result.dateFrom} — ${result.dateTo}`} />
            </div>
            <ReportPreviewTable result={result} />
          </>
        )}

        {!result && !runMutation.isPending && (
          <p className="text-sm text-gray-400">{t('reports.builder.noResult')}</p>
        )}

        {runMutation.isPending && <LoadingSkeleton rows={4} />}
      </div>

      {showDeleteConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="mx-4 w-full max-w-sm rounded-lg bg-white p-6 shadow-lg">
            <h3 className="text-base font-semibold text-gray-900">
              {t('reports.builder.deleteTemplate')}
            </h3>
            <p className="mt-2 text-sm text-gray-500">{t('reports.builder.deleteConfirm')}</p>
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowDeleteConfirm(false)}
                className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm font-medium text-gray-700"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => deleteMutation.mutate()}
                disabled={deleteMutation.isPending}
                className="rounded-md bg-red-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-700"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </PageShell>
  )
}
