import { useTranslation } from 'react-i18next'
import type { MetricMeta } from '@/api/reportBuilder'

interface MetricSelectorProps {
  metrics: MetricMeta[]
  selected: string[]
  incompatibleCodes: Set<string>
  onChange: (codes: string[]) => void
}

const SCOPE_ORDER = ['revenue', 'members', 'gx', 'pt', 'leads', 'cash'] as const

export function MetricSelector({
  metrics,
  selected,
  incompatibleCodes,
  onChange,
}: MetricSelectorProps) {
  const { t } = useTranslation()

  const grouped = new Map<string, MetricMeta[]>()
  for (const m of metrics) {
    const group = grouped.get(m.scope) ?? []
    group.push(m)
    grouped.set(m.scope, group)
  }

  function toggle(code: string) {
    if (selected.includes(code)) {
      onChange(selected.filter((c) => c !== code))
    } else {
      if (selected.length >= 10) return
      onChange([...selected, code])
    }
  }

  return (
    <div className="space-y-4">
      <h3 className="text-sm font-semibold text-gray-700">{t('reports.builder.selectMetrics')}</h3>
      {selected.length >= 10 && (
        <p className="text-xs text-amber-600">{t('reports.builder.maxMetrics')}</p>
      )}
      {SCOPE_ORDER.map((scope) => {
        const items = grouped.get(scope)
        if (!items) return null
        return (
          <div key={scope}>
            <p className="mb-1 text-xs font-medium uppercase text-gray-400">
              {t(`reports.builder.scope.${scope}`)}
            </p>
            <div className="flex flex-wrap gap-2">
              {items.map((m) => {
                const isSelected = selected.includes(m.code)
                const isIncompat = incompatibleCodes.has(m.code)
                return (
                  <button
                    key={m.code}
                    type="button"
                    onClick={() => toggle(m.code)}
                    disabled={isIncompat && !isSelected}
                    className={`rounded-md border px-3 py-1.5 text-xs font-medium transition ${
                      isSelected
                        ? 'border-blue-500 bg-blue-50 text-blue-700'
                        : isIncompat
                          ? 'cursor-not-allowed border-gray-200 bg-gray-50 text-gray-400'
                          : 'border-gray-200 bg-white text-gray-700 hover:border-blue-300'
                    }`}
                    title={isIncompat ? t('reports.builder.incompatible') : undefined}
                  >
                    {m.label}
                    <span className="ms-1 text-gray-400">({m.unit})</span>
                  </button>
                )
              })}
            </div>
          </div>
        )
      })}
    </div>
  )
}
