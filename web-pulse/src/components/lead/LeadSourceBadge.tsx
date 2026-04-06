import { useTranslation } from 'react-i18next'
import type { LeadSourceSummary } from '@/types/domain'

interface LeadSourceBadgeProps {
  source: LeadSourceSummary | null
}

export function LeadSourceBadge({ source }: LeadSourceBadgeProps) {
  const { i18n } = useTranslation()

  if (!source) return null

  const label = i18n.language === 'ar' ? source.nameAr : source.name

  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-gray-600">
      <span
        className="inline-block h-2.5 w-2.5 rounded-full"
        style={{ backgroundColor: source.color }}
      />
      {label}
    </span>
  )
}
