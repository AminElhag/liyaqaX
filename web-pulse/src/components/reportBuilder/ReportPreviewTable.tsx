import { useTranslation } from 'react-i18next'
import type { ReportResultResponse } from '@/api/reportBuilder'

interface ReportPreviewTableProps {
  result: ReportResultResponse
}

export function ReportPreviewTable({ result }: ReportPreviewTableProps) {
  const { t } = useTranslation()

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3">
        {result.fromCache && (
          <span
            className="inline-flex rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-medium text-blue-700"
            title={t('reports.builder.cachedHint')}
          >
            {t('reports.builder.cached')}
          </span>
        )}
        {result.truncated && (
          <span className="inline-flex rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700">
            {t('reports.builder.truncated')}
          </span>
        )}
        <span className="text-xs text-gray-500">
          {result.rowCount} rows
        </span>
      </div>

      <div className="overflow-x-auto rounded-lg border border-gray-200">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              {result.columns.map((col) => (
                <th
                  key={col}
                  className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500"
                >
                  {col}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200 bg-white">
            {result.rows.map((row, i) => (
              <tr key={i}>
                {result.columns.map((col) => (
                  <td
                    key={col}
                    className="whitespace-nowrap px-4 py-3 text-sm text-gray-700"
                  >
                    {String(row[col] ?? '')}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
