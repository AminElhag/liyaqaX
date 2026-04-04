import { createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { PageShell } from '@/components/layout/PageShell'

export const Route = createFileRoute('/')({
  component: DashboardPage,
})

function DashboardPage() {
  const { t } = useTranslation()

  return (
    <PageShell title={t('nav.dashboard')}>
      <div className="rounded-lg border border-dashed border-gray-300 p-12 text-center">
        <p className="text-gray-400">
          {t('nav.dashboard')} — coming soon
        </p>
      </div>
    </PageShell>
  )
}
