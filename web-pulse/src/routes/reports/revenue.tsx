import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { PageShell } from '@/components/layout/PageShell'
import { KpiCard } from '@/components/reports/KpiCard'
import { ReportDateRangePicker } from '@/components/reports/ReportDateRangePicker'
import { RevenueLineChart } from '@/components/reports/RevenueLineChart'
import { ReportTable } from '@/components/reports/ReportTable'
import { ExportCsvButton } from '@/components/reports/ExportCsvButton'
import { EmptyReportState } from '@/components/reports/EmptyReportState'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { getRevenueReport, exportReportCsvUrl, reportKeys } from '@/api/reports'
import type { TimePeriodRevenue } from '@/types/domain'

export const Route = createFileRoute('/reports/revenue')({
  component: RevenueReportPage,
})

function defaultFrom(): string {
  const d = new Date()
  d.setDate(1)
  return d.toISOString().slice(0, 10)
}

function defaultTo(): string {
  return new Date().toISOString().slice(0, 10)
}

function RevenueReportPage() {
  const { t } = useTranslation()
  const [from, setFrom] = useState(defaultFrom)
  const [to, setTo] = useState(defaultTo)

  const params = { from, to }
  const { data, isLoading } = useQuery({
    queryKey: reportKeys.revenue(params),
    queryFn: () => getRevenueReport(params),
    staleTime: 5 * 60 * 1000,
  })

  const columns = [
    { key: 'label', header: t('reports.period'), render: (r: TimePeriodRevenue) => r.label },
    { key: 'total', header: t('reports.revenue.total'), render: (r: TimePeriodRevenue) => r.totalRevenue.sar },
    { key: 'membership', header: t('reports.revenue.membership'), render: (r: TimePeriodRevenue) => r.membershipRevenue.sar },
    { key: 'pt', header: t('reports.revenue.pt'), render: (r: TimePeriodRevenue) => r.ptRevenue.sar },
    { key: 'other', header: t('reports.revenue.other'), render: (r: TimePeriodRevenue) => r.otherRevenue.sar },
    { key: 'count', header: t('reports.revenue.payments'), render: (r: TimePeriodRevenue) => r.paymentCount },
  ]

  return (
    <PageShell
      title={t('reports.revenue.title')}
      actions={<ExportCsvButton href={exportReportCsvUrl('revenue', params)} />}
    >
      <div className="space-y-6">
        <ReportDateRangePicker from={from} to={to} onFromChange={setFrom} onToChange={setTo} />

        {isLoading && <LoadingSkeleton rows={4} />}

        {data && (
          <>
            <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
              <KpiCard label={t('reports.revenue.total')} value={data.summary.totalRevenue.sar} />
              <KpiCard label={t('reports.revenue.membership')} value={data.summary.membershipRevenue.sar} />
              <KpiCard label={t('reports.revenue.pt')} value={data.summary.ptRevenue.sar} />
              <KpiCard
                label={t('reports.revenue.growth')}
                value={data.summary.growthPercent != null ? `${data.summary.growthPercent}%` : '-'}
                trend={data.summary.growthPercent}
              />
            </div>

            {data.periods.length > 0 ? (
              <>
                <RevenueLineChart periods={data.periods} />
                <ReportTable columns={columns} rows={data.periods} />
              </>
            ) : (
              <EmptyReportState />
            )}
          </>
        )}
      </div>
    </PageShell>
  )
}
