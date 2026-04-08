import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { PageShell } from '@/components/layout/PageShell'
import { MetricSelector } from '@/components/reportBuilder/MetricSelector'
import { DimensionSelector } from '@/components/reportBuilder/DimensionSelector'
import { FilterBuilder } from '@/components/reportBuilder/FilterBuilder'
import { CompatibilityWarning } from '@/components/reportBuilder/CompatibilityWarning'
import {
  getMetrics,
  getDimensions,
  getFilters,
  createTemplate,
  reportBuilderKeys,
} from '@/api/reportBuilder'
import type { DimensionMeta } from '@/api/reportBuilder'

export const Route = createFileRoute('/reports/builder/new')({
  component: NewReportPage,
})

function NewReportPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [selectedMetrics, setSelectedMetrics] = useState<string[]>([])
  const [selectedDimensions, setSelectedDimensions] = useState<string[]>([])
  const [filterValues, setFilterValues] = useState<Record<string, string | null>>({})

  const { data: metrics = [] } = useQuery({
    queryKey: reportBuilderKeys.metrics(),
    queryFn: getMetrics,
    staleTime: Infinity,
  })

  const { data: dimensions = [] } = useQuery({
    queryKey: reportBuilderKeys.dimensions(),
    queryFn: getDimensions,
    staleTime: Infinity,
  })

  const { data: filters = [] } = useQuery({
    queryKey: reportBuilderKeys.filters(),
    queryFn: getFilters,
    staleTime: Infinity,
  })

  const compatWarnings = useMemo(() => {
    if (selectedDimensions.length === 0 || selectedMetrics.length === 0) return []
    const warnings: string[] = []
    const dimMetas = selectedDimensions
      .map((code) => dimensions.find((d) => d.code === code))
      .filter(Boolean) as DimensionMeta[]

    for (const metricCode of selectedMetrics) {
      const metric = metrics.find((m) => m.code === metricCode)
      if (!metric) continue
      for (const dim of dimMetas) {
        if (!dim.compatibleMetricScopes.includes(metric.scope)) {
          warnings.push(`"${metric.label}" is not compatible with "${dim.label}"`)
        }
      }
    }
    return warnings
  }, [selectedMetrics, selectedDimensions, metrics, dimensions])

  const incompatibleMetricCodes = useMemo(() => {
    if (selectedDimensions.length === 0) return new Set<string>()
    const dimMetas = selectedDimensions
      .map((code) => dimensions.find((d) => d.code === code))
      .filter(Boolean) as DimensionMeta[]

    const codes = new Set<string>()
    for (const m of metrics) {
      for (const dim of dimMetas) {
        if (!dim.compatibleMetricScopes.includes(m.scope)) {
          codes.add(m.code)
          break
        }
      }
    }
    return codes
  }, [selectedDimensions, metrics, dimensions])

  const createMutation = useMutation({
    mutationFn: createTemplate,
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: reportBuilderKeys.templates() })
      navigate({ to: '/reports/builder/$templateId', params: { templateId: data.id } })
    },
  })

  const canSubmit =
    name.trim().length > 0 &&
    selectedMetrics.length > 0 &&
    selectedDimensions.length > 0 &&
    compatWarnings.length === 0 &&
    !createMutation.isPending

  function handleSubmit() {
    if (!canSubmit) return
    const cleanFilters = Object.fromEntries(
      Object.entries(filterValues).filter(([, v]) => v != null && v !== ''),
    )
    createMutation.mutate({
      name: name.trim(),
      description: description.trim() || undefined,
      metrics: selectedMetrics,
      dimensions: selectedDimensions,
      filters: Object.keys(cleanFilters).length > 0 ? cleanFilters : undefined,
    })
  }

  return (
    <PageShell title={t('reports.builder.newReport')}>
      <div className="mx-auto max-w-3xl space-y-6">
        <div>
          <label className="mb-1 block text-sm font-medium text-gray-700">
            {t('reports.builder.reportName')}
          </label>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={200}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
          />
        </div>

        <div>
          <label className="mb-1 block text-sm font-medium text-gray-700">
            {t('reports.builder.reportDescription')}
          </label>
          <input
            type="text"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={500}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
          />
        </div>

        <MetricSelector
          metrics={metrics}
          selected={selectedMetrics}
          incompatibleCodes={incompatibleMetricCodes}
          onChange={setSelectedMetrics}
        />

        <DimensionSelector
          dimensions={dimensions}
          selected={selectedDimensions}
          onChange={setSelectedDimensions}
        />

        <CompatibilityWarning messages={compatWarnings} />

        <FilterBuilder
          availableFilters={filters}
          values={filterValues}
          onChange={setFilterValues}
        />

        <div className="flex justify-end gap-3 border-t border-gray-200 pt-4">
          <button
            type="button"
            onClick={() => navigate({ to: '/reports/builder' })}
            className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={!canSubmit}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {t('reports.builder.saveReport')}
          </button>
        </div>
      </div>
    </PageShell>
  )
}
