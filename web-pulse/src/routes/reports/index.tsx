import { createFileRoute, Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PageShell } from '@/components/layout/PageShell'
import { PermissionGate } from '@/components/shared/PermissionGate'
import { Permission, type PermissionCode } from '@/types/permissions'

export const Route = createFileRoute('/reports/')({
  component: ReportsHubPage,
})

interface ReportCard {
  title: string
  description: string
  to: string
  permission: PermissionCode
}

function ReportsHubPage() {
  const { t } = useTranslation()

  const cards: ReportCard[] = [
    {
      title: t('reports.revenue.title'),
      description: t('reports.revenue.description'),
      to: '/reports/revenue',
      permission: Permission.REPORT_REVENUE_VIEW,
    },
    {
      title: t('reports.retention.title'),
      description: t('reports.retention.description'),
      to: '/reports/retention',
      permission: Permission.REPORT_RETENTION_VIEW,
    },
    {
      title: t('reports.leads.title'),
      description: t('reports.leads.description'),
      to: '/reports/leads',
      permission: Permission.REPORT_LEADS_VIEW,
    },
    {
      title: t('reports.cashDrawer.title'),
      description: t('reports.cashDrawer.description'),
      to: '/reports/cash-drawer',
      permission: Permission.REPORT_CASH_DRAWER_VIEW,
    },
  ]

  return (
    <PageShell title={t('reports.hubTitle')}>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {cards.map((card) => (
          <PermissionGate key={card.to} permission={card.permission}>
            <Link
              to={card.to}
              className="block rounded-lg border border-gray-200 bg-white p-5 transition-shadow hover:shadow-md"
            >
              <h3 className="text-base font-semibold text-gray-900">{card.title}</h3>
              <p className="mt-2 text-sm text-gray-500">{card.description}</p>
              <span className="mt-3 inline-block text-sm font-medium text-blue-600">
                {t('reports.viewReport')} &rarr;
              </span>
            </Link>
          </PermissionGate>
        ))}
      </div>
    </PageShell>
  )
}
