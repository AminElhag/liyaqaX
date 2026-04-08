import { useTranslation } from 'react-i18next'
import type { FilterMeta } from '@/api/reportBuilder'

interface FilterBuilderProps {
  availableFilters: FilterMeta[]
  values: Record<string, string | null>
  onChange: (values: Record<string, string | null>) => void
}

export function FilterBuilder({
  availableFilters,
  values,
  onChange,
}: FilterBuilderProps) {
  const { t } = useTranslation()

  function setFilter(code: string, value: string | null) {
    onChange({ ...values, [code]: value || null })
  }

  return (
    <div className="space-y-3">
      <h3 className="text-sm font-semibold text-gray-700">
        {t('reports.builder.configureFilters')}
      </h3>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {availableFilters.map((f) => (
          <div key={f.code}>
            <label className="mb-1 block text-xs font-medium text-gray-500">{f.label}</label>
            <input
              type="text"
              placeholder={`${f.label} ID`}
              value={values[f.code] ?? ''}
              onChange={(e) => setFilter(f.code, e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm"
            />
          </div>
        ))}
      </div>
    </div>
  )
}
