import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { PageShell } from '@/components/layout/PageShell'
import { KpiCard } from '@/components/reports/KpiCard'
import { ReportDateRangePicker } from '@/components/reports/ReportDateRangePicker'
import { LeadFunnelChart } from '@/components/reports/LeadFunnelChart'
import { ReportTable } from '@/components/reports/ReportTable'
import { ExportCsvButton } from '@/components/reports/ExportCsvButton'
import { EmptyReportState } from '@/components/reports/EmptyReportState'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { getLeadReport, exportReportCsvUrl, reportKeys } from '@/api/reports'
import type { TimePeriodLeads, LeadSourceStat, LostReasonCount } from '@/types/domain'

export const Route = createFileRoute('/reports/leads')({
  component: LeadReportPage,
})

function defaultFrom(): string {
  const d = new Date()
  d.setDate(1)
  return d.toISOString().slice(0, 10)
}

function defaultTo(): string {
  return new Date().toISOString().slice(0, 10)
}

function LeadReportPage() {
  const { t } = useTranslation()
  const [from, setFrom] = useState(defaultFrom)
  const [to, setTo] = useState(defaultTo)

  const params = { from, to }
  const { data, isLoading } = useQuery({
    queryKey: reportKeys.leads(params),
    queryFn: () => getLeadReport(params),
    staleTime: 5 * 60 * 1000,
  })

  const periodColumns = [
    { key: 'label', header: t('reports.period'), render: (r: TimePeriodLeads) => r.label },
    { key: 'new', header: t('reports.leads.newLeads'), render: (r: TimePeriodLeads) => r.newLeads },
    { key: 'conv', header: t('reports.leads.converted'), render: (r: TimePeriodLeads) => r.converted },
    { key: 'lost', header: t('reports.leads.lost'), render: (r: TimePeriodLeads) => r.lost },
    { key: 'rate', header: t('reports.leads.convRate'), render: (r: TimePeriodLeads) => `${r.conversionRate}%` },
  ]

  const sourceColumns = [
    {
      key: 'name',
      header: t('reports.leads.source'),
      render: (r: LeadSourceStat) => r.sourceName,
    },
    { key: 'count', header: t('reports.leads.count'), render: (r: LeadSourceStat) => r.count },
    { key: 'rate', header: t('reports.leads.convRate'), render: (r: LeadSourceStat) => `${r.conversionRate}%` },
  ]

  const lostColumns = [
    { key: 'reason', header: t('reports.leads.reason'), render: (r: LostReasonCount) => r.reason },
    { key: 'count', header: t('reports.leads.count'), render: (r: LostReasonCount) => r.count },
  ]

  return (
    <PageShell
      title={t('reports.leads.title')}
      actions={<ExportCsvButton href={exportReportCsvUrl('leads', params)} />}
    >
      <div className="space-y-6">
        <ReportDateRangePicker from={from} to={to} onFromChange={setFrom} onToChange={setTo} />

        {isLoading && <LoadingSkeleton rows={4} />}

        {data && (
          <>
            <div className="grid grid-cols-2 gap-4 lg:grid-cols-3">
              <KpiCard label={t('reports.leads.totalLeads')} value={data.summary.totalLeads} />
              <KpiCard label={t('reports.leads.convRate')} value={`${data.summary.conversionRate}%`} />
              <KpiCard
                label={t('reports.leads.avgDays')}
                value={data.summary.avgDaysToConvert != null ? data.summary.avgDaysToConvert.toFixed(1) : '-'}
              />
            </div>

            <LeadFunnelChart byStage={data.summary.byStage} />

            {data.summary.topSources.length > 0 && (
              <div>
                <h3 className="mb-3 text-base font-semibold text-gray-900">{t('reports.leads.topSources')}</h3>
                <ReportTable columns={sourceColumns} rows={data.summary.topSources} />
              </div>
            )}

            {data.periods.length > 0 ? (
              <ReportTable columns={periodColumns} rows={data.periods} />
            ) : (
              <EmptyReportState />
            )}

            {data.lostReasons.length > 0 && (
              <div>
                <h3 className="mb-3 text-base font-semibold text-gray-900">{t('reports.leads.lostReasons')}</h3>
                <ReportTable columns={lostColumns} rows={data.lostReasons} />
              </div>
            )}
          </>
        )}
      </div>
    </PageShell>
  )
}
