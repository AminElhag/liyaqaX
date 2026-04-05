import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/cn'
import type { ZatcaStatus } from '@/types/domain'

interface ZatcaStatusBadgeProps {
  status: string
}

const statusStyles: Record<ZatcaStatus, string> = {
  pending: 'bg-amber-100 text-amber-700',
  generated: 'bg-green-100 text-green-700',
  submitted: 'bg-blue-100 text-blue-700',
  accepted: 'bg-green-100 text-green-700',
  rejected: 'bg-red-100 text-red-700',
}

export function ZatcaStatusBadge({ status }: ZatcaStatusBadgeProps) {
  const { t } = useTranslation()
  const zatcaStatus = status as ZatcaStatus

  return (
    <span
      className={cn(
        'inline-flex rounded-full px-2 py-0.5 text-xs font-medium',
        statusStyles[zatcaStatus] ?? 'bg-gray-100 text-gray-700',
      )}
    >
      {t(`invoice.zatcaStatus.${zatcaStatus}`)}
    </span>
  )
}
