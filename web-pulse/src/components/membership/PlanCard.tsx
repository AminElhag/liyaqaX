import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/cn'
import { formatCurrency } from '@/lib/formatCurrency'
import { PermissionGate } from '@/components/shared/PermissionGate'
import { Permission } from '@/types/permissions'
import type { MembershipPlanSummary } from '@/types/domain'

interface PlanCardProps {
  plan: MembershipPlanSummary
  onEdit: (plan: MembershipPlanSummary) => void
}

export function PlanCard({ plan, onEdit }: PlanCardProps) {
  const { t, i18n } = useTranslation()
  const name = i18n.language === 'ar' ? plan.nameAr : plan.nameEn
  const price = formatCurrency(plan.priceHalalas, i18n.language)

  return (
    <div
      className={cn(
        'rounded-lg border bg-white p-5 shadow-sm transition-colors',
        plan.isActive ? 'border-gray-200' : 'border-gray-100 opacity-60',
      )}
    >
      <div className="mb-3 flex items-start justify-between">
        <div>
          <h3 className="text-lg font-semibold text-gray-900">{name}</h3>
          <p className="text-2xl font-bold text-gray-900">{price}</p>
        </div>
        <span
          className={cn(
            'inline-flex rounded-full px-2 py-0.5 text-xs font-medium',
            plan.isActive
              ? 'bg-green-100 text-green-700'
              : 'bg-gray-100 text-gray-500',
          )}
        >
          {plan.isActive ? t('common.active') : t('common.inactive')}
        </span>
      </div>

      <div className="mb-4 text-sm text-gray-600">
        {plan.durationDays} {t('membershipPlans.durationDays')}
      </div>

      <PermissionGate permission={Permission.MEMBERSHIP_PLAN_UPDATE}>
        <button
          type="button"
          onClick={() => onEdit(plan)}
          className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50"
        >
          {t('membershipPlans.editPlan')}
        </button>
      </PermissionGate>
    </div>
  )
}
