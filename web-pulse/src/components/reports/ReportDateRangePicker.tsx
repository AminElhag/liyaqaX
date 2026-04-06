import { useTranslation } from 'react-i18next'

interface ReportDateRangePickerProps {
  from: string
  to: string
  onFromChange: (value: string) => void
  onToChange: (value: string) => void
}

const presets = [
  { key: 'thisWeek', days: 7 },
  { key: 'thisMonth', days: 30 },
  { key: 'lastMonth', days: 60 },
  { key: 'last3Months', days: 90 },
  { key: 'last12Months', days: 365 },
] as const

function formatDate(date: Date): string {
  return date.toISOString().slice(0, 10)
}

export function ReportDateRangePicker({
  from,
  to,
  onFromChange,
  onToChange,
}: ReportDateRangePickerProps) {
  const { t } = useTranslation()

  function applyPreset(days: number) {
    const end = new Date()
    const start = new Date()
    start.setDate(start.getDate() - days)
    onFromChange(formatDate(start))
    onToChange(formatDate(end))
  }

  return (
    <div className="flex flex-wrap items-center gap-3">
      <div className="flex items-center gap-2">
        <label className="text-sm text-gray-600">{t('reports.from')}</label>
        <input
          type="date"
          value={from}
          onChange={(e) => onFromChange(e.target.value)}
          className="rounded-md border border-gray-300 px-3 py-1.5 text-sm"
        />
      </div>
      <div className="flex items-center gap-2">
        <label className="text-sm text-gray-600">{t('reports.to')}</label>
        <input
          type="date"
          value={to}
          onChange={(e) => onToChange(e.target.value)}
          className="rounded-md border border-gray-300 px-3 py-1.5 text-sm"
        />
      </div>
      <div className="flex gap-1">
        {presets.map((p) => (
          <button
            key={p.key}
            type="button"
            onClick={() => applyPreset(p.days)}
            className="rounded-md bg-gray-100 px-2.5 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-200"
          >
            {t(`reports.preset.${p.key}`)}
          </button>
        ))}
      </div>
    </div>
  )
}
