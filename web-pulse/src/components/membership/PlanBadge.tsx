import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/cn'
import { formatCurrency } from '@/lib/formatCurrency'
import type { MembershipPlanSummary } from '@/types/domain'

interface PlanBadgeProps {
  plan: MembershipPlanSummary
  size?: 'sm' | 'md'
}

export function PlanBadge({ plan, size = 'sm' }: PlanBadgeProps) {
  const { i18n } = useTranslation()
  const name = i18n.language === 'ar' ? plan.nameAr : plan.nameEn
  const price = formatCurrency(plan.priceHalalas, i18n.language)

  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-full font-medium',
        plan.isActive
          ? 'bg-green-100 text-green-700'
          : 'bg-gray-100 text-gray-500',
        size === 'sm' && 'px-2 py-0.5 text-xs',
        size === 'md' && 'px-3 py-1 text-sm',
      )}
    >
      {name}
      <span className="opacity-70">{price}</span>
    </span>
  )
}
