import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { PageShell } from '@/components/layout/PageShell'
import { KpiCard } from '@/components/reports/KpiCard'
import { ReportDateRangePicker } from '@/components/reports/ReportDateRangePicker'
import { RetentionBarChart } from '@/components/reports/RetentionBarChart'
import { ReportTable } from '@/components/reports/ReportTable'
import { ExportCsvButton } from '@/components/reports/ExportCsvButton'
import { EmptyReportState } from '@/components/reports/EmptyReportState'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { getRetentionReport, exportReportCsvUrl, reportKeys } from '@/api/reports'
import type { TimePeriodRetention, AtRiskMember } from '@/types/domain'

export const Route = createFileRoute('/reports/retention')({
  component: RetentionReportPage,
})

function defaultFrom(): string {
  const d = new Date()
  d.setDate(1)
  return d.toISOString().slice(0, 10)
}

function defaultTo(): string {
  return new Date().toISOString().slice(0, 10)
}

function RetentionReportPage() {
  const { t } = useTranslation()
  const [from, setFrom] = useState(defaultFrom)
  const [to, setTo] = useState(defaultTo)

  const params = { from, to }
  const { data, isLoading } = useQuery({
    queryKey: reportKeys.retention(params),
    queryFn: () => getRetentionReport(params),
    staleTime: 5 * 60 * 1000,
  })

  const periodColumns = [
    { key: 'label', header: t('reports.period'), render: (r: TimePeriodRetention) => r.label },
    { key: 'new', header: t('reports.retention.newMembers'), render: (r: TimePeriodRetention) => r.newMembers },
    { key: 'renewals', header: t('reports.retention.renewals'), render: (r: TimePeriodRetention) => r.renewals },
    { key: 'expired', header: t('reports.retention.expired'), render: (r: TimePeriodRetention) => r.expired },
    { key: 'activeEnd', header: t('reports.retention.activeAtEnd'), render: (r: TimePeriodRetention) => r.activeAtEnd },
    { key: 'churn', header: t('reports.retention.churnRate'), render: (r: TimePeriodRetention) => `${r.churnRate}%` },
  ]

  const atRiskColumns = [
    { key: 'name', header: t('reports.retention.member'), render: (r: AtRiskMember) => r.memberName },
    { key: 'plan', header: t('reports.retention.plan'), render: (r: AtRiskMember) => r.membershipPlan },
    { key: 'expires', header: t('reports.retention.expiresAt'), render: (r: AtRiskMember) => r.expiresAt },
    { key: 'days', header: t('reports.retention.daysLeft'), render: (r: AtRiskMember) => r.daysUntilExpiry },
  ]

  return (
    <PageShell
      title={t('reports.retention.title')}
      actions={<ExportCsvButton href={exportReportCsvUrl('retention', params)} />}
    >
      <div className="space-y-6">
        <ReportDateRangePicker from={from} to={to} onFromChange={setFrom} onToChange={setTo} />

        {isLoading && <LoadingSkeleton rows={4} />}

        {data && (
          <>
            <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
              <KpiCard label={t('reports.retention.active')} value={data.summary.activeMembers} />
              <KpiCard label={t('reports.retention.churnRate')} value={`${data.summary.churnRate}%`} />
              <KpiCard label={t('reports.retention.retentionRate')} value={`${data.summary.retentionRate}%`} />
              <KpiCard label={t('reports.retention.expiringNext30')} value={data.summary.expiringNext30Days} />
            </div>

            {data.periods.length > 0 ? (
              <>
                <RetentionBarChart periods={data.periods} />
                <ReportTable columns={periodColumns} rows={data.periods} />
              </>
            ) : (
              <EmptyReportState />
            )}

            {data.atRisk.length > 0 && (
              <div>
                <h3 className="mb-3 text-base font-semibold text-gray-900">
                  {t('reports.retention.atRiskTitle')}
                </h3>
                <ReportTable columns={atRiskColumns} rows={data.atRisk} />
              </div>
            )}
          </>
        )}
      </div>
    </PageShell>
  )
}
