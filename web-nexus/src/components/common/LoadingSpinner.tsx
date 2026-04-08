import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/cn'

export function LoadingSpinner({ className }: { className?: string }) {
  const { t } = useTranslation()

  return (
    <div className={cn('flex items-center justify-center py-12', className)}>
      <div className="flex flex-col items-center gap-3">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-gray-200 border-t-blue-600" />
        <p className="text-sm text-gray-500">{t('common.loading')}</p>
      </div>
    </div>
  )
}
