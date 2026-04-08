import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { PageShell } from '@/components/layout/PageShell'
import { KpiCard } from '@/components/reports/KpiCard'
import { ReportDateRangePicker } from '@/components/reports/ReportDateRangePicker'
import { CashDrawerBarChart } from '@/components/reports/CashDrawerBarChart'
import { ReportTable } from '@/components/reports/ReportTable'
import { ExportCsvButton } from '@/components/reports/ExportCsvButton'
import { EmptyReportState } from '@/components/reports/EmptyReportState'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { getCashDrawerReport, exportReportCsvUrl, reportKeys } from '@/api/reports'
import type { TimePeriodCashDrawer } from '@/types/domain'

export const Route = createFileRoute('/reports/cash-drawer')({
  component: CashDrawerReportPage,
})

function defaultFrom(): string {
  const d = new Date()
  d.setDate(1)
  return d.toISOString().slice(0, 10)
}

function defaultTo(): string {
  return new Date().toISOString().slice(0, 10)
}

function CashDrawerReportPage() {
  const { t } = useTranslation()
  const [from, setFrom] = useState(defaultFrom)
  const [to, setTo] = useState(defaultTo)

  const params = { from, to }
  const { data, isLoading } = useQuery({
    queryKey: reportKeys.cashDrawer(params),
    queryFn: () => getCashDrawerReport(params),
    staleTime: 5 * 60 * 1000,
  })

  const columns = [
    { key: 'label', header: t('reports.period'), render: (r: TimePeriodCashDrawer) => r.label },
    { key: 'sessions', header: t('reports.cashDrawer.sessions'), render: (r: TimePeriodCashDrawer) => r.sessionCount },
    { key: 'cashIn', header: t('reports.cashDrawer.cashIn'), render: (r: TimePeriodCashDrawer) => r.totalCashIn.sar },
    { key: 'cashOut', header: t('reports.cashDrawer.cashOut'), render: (r: TimePeriodCashDrawer) => r.totalCashOut.sar },
    { key: 'net', header: t('reports.cashDrawer.netCash'), render: (r: TimePeriodCashDrawer) => r.netCash.sar },
    { key: 'shortages', header: t('reports.cashDrawer.shortages'), render: (r: TimePeriodCashDrawer) => r.shortages.sar },
    { key: 'surpluses', header: t('reports.cashDrawer.surpluses'), render: (r: TimePeriodCashDrawer) => r.surpluses.sar },
  ]

  return (
    <PageShell
      title={t('reports.cashDrawer.title')}
      actions={<ExportCsvButton href={exportReportCsvUrl('cash-drawer', params)} />}
    >
      <div className="space-y-6">
        <ReportDateRangePicker from={from} to={to} onFromChange={setFrom} onToChange={setTo} />

        {isLoading && <LoadingSkeleton rows={4} />}

        {data && (
          <>
            <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
              <KpiCard label={t('reports.cashDrawer.totalSessions')} value={data.summary.totalSessions} />
              <KpiCard label={t('reports.cashDrawer.netCash')} value={data.summary.netCash.sar} />
              <KpiCard label={t('reports.cashDrawer.shortages')} value={data.summary.totalShortages.sar} />
              <KpiCard label={t('reports.cashDrawer.reconciliation')} value={`${data.summary.reconciliationRate}%`} />
            </div>

            {data.periods.length > 0 ? (
              <>
                <CashDrawerBarChart periods={data.periods} />
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
