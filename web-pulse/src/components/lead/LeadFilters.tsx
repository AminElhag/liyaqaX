import { useTranslation } from 'react-i18next'
import type { LeadSource } from '@/types/domain'

interface LeadFiltersProps {
  stage: string
  onStageChange: (stage: string) => void
  sources: LeadSource[]
  sourceId: string
  onSourceChange: (sourceId: string) => void
  search: string
  onSearchChange: (search: string) => void
}

const STAGES: Array<{ value: string; key: string }> = [
  { value: '', key: 'common.all' },
  { value: 'new', key: 'leads.stage.new' },
  { value: 'contacted', key: 'leads.stage.contacted' },
  { value: 'interested', key: 'leads.stage.interested' },
  { value: 'converted', key: 'leads.stage.converted' },
  { value: 'lost', key: 'leads.stage.lost' },
]

export function LeadFilters({
  stage,
  onStageChange,
  sources,
  sourceId,
  onSourceChange,
  search,
  onSearchChange,
}: LeadFiltersProps) {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'

  return (
    <div className="flex flex-wrap items-center gap-3">
      <input
        type="text"
        placeholder={t('leads.searchPlaceholder')}
        value={search}
        onChange={(e) => onSearchChange(e.target.value)}
        className="rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
      />

      <select
        value={stage}
        onChange={(e) => onStageChange(e.target.value)}
        className="rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
      >
        {STAGES.map((s) => (
          <option key={s.value} value={s.value}>
            {t(s.key)}
          </option>
        ))}
      </select>

      <select
        value={sourceId}
        onChange={(e) => onSourceChange(e.target.value)}
        className="rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
      >
        <option value="">{t('leads.allSources')}</option>
        {sources.map((s) => (
          <option key={s.id} value={s.id}>
            {isAr ? s.nameAr : s.name}
          </option>
        ))}
      </select>
    </div>
  )
}
