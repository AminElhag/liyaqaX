import { useTranslation } from 'react-i18next'

export function EmptyState({ message }: { message?: string }) {
  const { t } = useTranslation()

  return (
    <div className="flex flex-col items-center justify-center py-16 text-center">
      <div className="mb-3 text-4xl text-gray-300">--</div>
      <p className="text-sm text-gray-500">
        {message ?? t('common.no_results')}
      </p>
    </div>
  )
}
