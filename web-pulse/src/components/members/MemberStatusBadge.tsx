import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/cn'
import type { MembershipStatus } from '@/types/domain'

interface MemberStatusBadgeProps {
  status: MembershipStatus
}

const statusStyles: Record<MembershipStatus, string> = {
  pending: 'bg-amber-100 text-amber-700',
  active: 'bg-green-100 text-green-700',
  frozen: 'bg-blue-100 text-blue-700',
  expired: 'bg-orange-100 text-orange-700',
  terminated: 'bg-red-100 text-red-700',
  lapsed: 'bg-red-100 text-red-700',
}

export function MemberStatusBadge({ status }: MemberStatusBadgeProps) {
  const { t } = useTranslation()

  return (
    <span
      className={cn(
        'inline-flex rounded-full px-2 py-0.5 text-xs font-medium',
        statusStyles[status],
      )}
    >
      {t(`members.status.${status}`)}
    </span>
  )
}
