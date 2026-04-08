import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/cn'
import type { CashDrawerEntry } from '@/types/domain'

interface EntryListProps {
  entries: CashDrawerEntry[]
}

const TYPE_STYLES: Record<string, string> = {
  cash_in: 'bg-green-100 text-green-700',
  cash_out: 'bg-red-100 text-red-700',
  float_adjustment: 'bg-blue-100 text-blue-700',
}

export function EntryList({ entries }: EntryListProps) {
  const { t } = useTranslation()

  if (entries.length === 0) {
    return (
      <div className="py-8 text-center text-sm text-gray-500">
        {t('cash_drawer.no_entries')}
      </div>
    )
  }

  return (
    <div className="overflow-hidden rounded-lg border border-gray-200">
      <table className="min-w-full divide-y divide-gray-200">
        <thead className="bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
              {t('cash_drawer.entry_type')}
            </th>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
              {t('cash_drawer.amount_sar')}
            </th>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
              {t('cash_drawer.description')}
            </th>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
              {t('cash_drawer.recorded_by')}
            </th>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
              {t('cash_drawer.recorded_at')}
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-200 bg-white">
          {entries.map((entry) => (
            <tr key={entry.id}>
              <td className="whitespace-nowrap px-4 py-3">
                <span
                  className={cn(
                    'inline-flex rounded-full px-2 py-0.5 text-xs font-medium',
                    TYPE_STYLES[entry.entryType] ?? 'bg-gray-100 text-gray-700',
                  )}
                >
                  {t(`cash_drawer.entry.${entry.entryType}`)}
                </span>
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-sm font-medium text-gray-900">
                {entry.amount.sar} SAR
              </td>
              <td className="px-4 py-3 text-sm text-gray-700">
                {entry.description}
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-500">
                {entry.recordedBy.firstName} {entry.recordedBy.lastName}
              </td>
              <td className="whitespace-nowrap px-4 py-3 text-sm text-gray-500">
                {new Date(entry.recordedAt).toLocaleTimeString()}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
