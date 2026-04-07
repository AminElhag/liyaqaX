import { useTranslation } from 'react-i18next'
import type { DimensionMeta } from '@/api/reportBuilder'

interface DimensionSelectorProps {
  dimensions: DimensionMeta[]
  selected: string[]
  onChange: (codes: string[]) => void
}

export function DimensionSelector({
  dimensions,
  selected,
  onChange,
}: DimensionSelectorProps) {
  const { t } = useTranslation()

  const primary = selected[0] ?? null
  const secondary = selected[1] ?? null

  function setPrimary(code: string) {
    if (secondary && secondary === code) {
      onChange([code])
    } else if (secondary) {
      onChange([code, secondary])
    } else {
      onChange([code])
    }
  }

  function toggleSecondary(code: string) {
    if (secondary === code) {
      onChange(primary ? [primary] : [])
    } else {
      onChange(primary ? [primary, code] : [code])
    }
  }

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-semibold text-gray-700">
        {t('reports.builder.selectDimensions')}
      </h3>

      <div>
        <p className="mb-2 text-xs font-medium text-gray-400">Primary dimension (required)</p>
        <div className="flex flex-wrap gap-2">
          {dimensions.map((d) => (
            <button
              key={d.code}
              type="button"
              onClick={() => setPrimary(d.code)}
              className={`rounded-md border px-3 py-1.5 text-xs font-medium transition ${
                primary === d.code
                  ? 'border-blue-500 bg-blue-50 text-blue-700'
                  : 'border-gray-200 bg-white text-gray-700 hover:border-blue-300'
              }`}
            >
              {d.label}
            </button>
          ))}
        </div>
      </div>

      {primary && (
        <div>
          <p className="mb-2 text-xs font-medium text-gray-400">Second dimension (optional)</p>
          <div className="flex flex-wrap gap-2">
            {dimensions
              .filter((d) => d.code !== primary)
              .map((d) => (
                <button
                  key={d.code}
                  type="button"
                  onClick={() => toggleSecondary(d.code)}
                  className={`rounded-md border px-3 py-1.5 text-xs font-medium transition ${
                    secondary === d.code
                      ? 'border-blue-500 bg-blue-50 text-blue-700'
                      : 'border-gray-200 bg-white text-gray-700 hover:border-blue-300'
                  }`}
                >
                  {d.label}
                </button>
              ))}
          </div>
        </div>
      )}
    </div>
  )
}
