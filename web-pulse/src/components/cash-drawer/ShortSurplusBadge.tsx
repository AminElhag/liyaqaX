import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/cn'
import type { MoneyValue } from '@/types/domain'

interface ShortSurplusBadgeProps {
  difference: MoneyValue | null
  className?: string
}

export function ShortSurplusBadge({
  difference,
  className,
}: ShortSurplusBadgeProps) {
  const { t } = useTranslation()

  if (!difference) return null

  const halalas = difference.halalas

  if (halalas === 0) {
    return (
      <span
        className={cn(
          'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
          'bg-gray-100 text-gray-700',
          className,
        )}
      >
        {t('cash_drawer.difference.exact')}
      </span>
    )
  }

  if (halalas > 0) {
    return (
      <span
        className={cn(
          'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
          'bg-green-100 text-green-700',
          className,
        )}
      >
        {t('cash_drawer.difference.surplus')} +{difference.sar} SAR
      </span>
    )
  }

  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
        'bg-red-100 text-red-700',
        className,
      )}
    >
      {t('cash_drawer.difference.shortage')} {difference.sar} SAR
    </span>
  )
}
