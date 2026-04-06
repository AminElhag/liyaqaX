import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/useAuthStore'

interface ExportCsvButtonProps {
  href: string
}

export function ExportCsvButton({ href }: ExportCsvButtonProps) {
  const { t } = useTranslation()
  const token = useAuthStore((s) => s.accessToken)

  function handleExport() {
    const link = document.createElement('a')
    link.href = href + (href.includes('?') ? '&' : '?') + `_token=${token}`
    link.setAttribute('download', '')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  return (
    <button
      type="button"
      onClick={handleExport}
      className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
    >
      {t('reports.exportCsv')}
    </button>
  )
}
