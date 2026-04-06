import { useTranslation } from 'react-i18next'

export function EmptyReportState() {
  const { t } = useTranslation()
  return (
    <div className="rounded-lg border border-dashed border-gray-300 px-6 py-12 text-center">
      <p className="text-sm text-gray-500">{t('reports.noData')}</p>
    </div>
  )
}
