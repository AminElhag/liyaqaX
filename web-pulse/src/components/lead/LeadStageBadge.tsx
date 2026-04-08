import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/cn'
import type { LeadStage } from '@/types/domain'

interface LeadStageBadgeProps {
  stage: LeadStage
}

const stageStyles: Record<LeadStage, string> = {
  new: 'bg-blue-100 text-blue-700',
  contacted: 'bg-amber-100 text-amber-700',
  interested: 'bg-purple-100 text-purple-700',
  converted: 'bg-green-100 text-green-700',
  lost: 'bg-red-100 text-red-700',
}

export function LeadStageBadge({ stage }: LeadStageBadgeProps) {
  const { t } = useTranslation()

  return (
    <span
      className={cn(
        'inline-flex rounded-full px-2 py-0.5 text-xs font-medium',
        stageStyles[stage],
      )}
    >
      {t(`leads.stage.${stage}`)}
    </span>
  )
}
