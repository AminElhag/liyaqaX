import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/cn'

interface PaginationProps {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
}

export function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  const { t } = useTranslation()

  if (totalPages <= 1) return null

  return (
    <div className="flex items-center justify-center gap-2 py-4">
      <button
        type="button"
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
        className={cn(
          'rounded-md border px-3 py-1.5 text-sm font-medium',
          page <= 0
            ? 'cursor-not-allowed border-gray-200 text-gray-400'
            : 'border-gray-300 text-gray-700 hover:bg-gray-50',
        )}
      >
        {t('common.back')}
      </button>
      <span className="text-sm text-gray-600">
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
        className={cn(
          'rounded-md border px-3 py-1.5 text-sm font-medium',
          page >= totalPages - 1
            ? 'cursor-not-allowed border-gray-200 text-gray-400'
            : 'border-gray-300 text-gray-700 hover:bg-gray-50',
        )}
      >
        &rarr;
      </button>
    </div>
  )
}
