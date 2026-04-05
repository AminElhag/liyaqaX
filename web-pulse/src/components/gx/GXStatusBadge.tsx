import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/cn'
import type { GXInstanceStatus, GXBookingStatus } from '@/types/domain'

type StatusType = GXInstanceStatus | GXBookingStatus

interface GXStatusBadgeProps {
  status: StatusType
  variant?: 'instance' | 'booking'
}

const instanceStyles: Record<GXInstanceStatus, string> = {
  scheduled: 'bg-blue-100 text-blue-700',
  'in-progress': 'bg-green-100 text-green-700',
  completed: 'bg-gray-100 text-gray-700',
  cancelled: 'bg-red-100 text-red-700',
}

const bookingStyles: Record<GXBookingStatus, string> = {
  confirmed: 'bg-green-100 text-green-700',
  promoted: 'bg-green-100 text-green-700',
  waitlist: 'bg-amber-100 text-amber-700',
  cancelled: 'bg-red-100 text-red-700',
}

export function GXStatusBadge({
  status,
  variant = 'instance',
}: GXStatusBadgeProps) {
  const { t } = useTranslation()
  const styles =
    variant === 'booking'
      ? bookingStyles[status as GXBookingStatus]
      : instanceStyles[status as GXInstanceStatus]

  return (
    <span
      className={cn(
        'inline-flex rounded-full px-2 py-0.5 text-xs font-medium',
        styles,
      )}
    >
      {t(`gx.status.${status}`)}
    </span>
  )
}
