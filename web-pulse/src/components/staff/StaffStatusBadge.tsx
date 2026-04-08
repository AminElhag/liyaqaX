import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/cn'

interface StaffStatusBadgeProps {
  isActive: boolean
}

export function StaffStatusBadge({ isActive }: StaffStatusBadgeProps) {
  const { t } = useTranslation()

  return (
    <span
      className={cn(
        'inline-flex rounded-full px-2 py-0.5 text-xs font-medium',
        isActive
          ? 'bg-green-100 text-green-700'
          : 'bg-gray-100 text-gray-600',
      )}
    >
      {isActive ? t('common.active') : t('common.inactive')}
    </span>
  )
}
