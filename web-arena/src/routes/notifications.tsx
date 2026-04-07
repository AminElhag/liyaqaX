import { createFileRoute } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useNotifications } from '@/hooks/useNotifications'
import { NotificationItem } from '@/components/notifications/NotificationItem'

export const Route = createFileRoute('/notifications')({
  component: NotificationsPage,
})

function NotificationsPage() {
  const { t } = useTranslation()
  const { notifications, isLoading, markRead, markAllRead, unreadCount } = useNotifications()

  return (
    <div className="mx-auto max-w-2xl">
      <div className="flex items-center justify-between border-b border-gray-200 px-4 py-4">
        <h1 className="text-lg font-bold text-gray-900">{t('notifications.title')}</h1>
        {unreadCount > 0 && (
          <button
            type="button"
            onClick={() => markAllRead()}
            className="text-sm font-medium text-blue-600 hover:text-blue-700"
          >
            {t('notifications.markAllRead')}
          </button>
        )}
      </div>

      {isLoading && (
        <p className="py-12 text-center text-sm text-gray-400">{t('common.loading')}</p>
      )}

      {!isLoading && notifications.length === 0 && (
        <p className="py-12 text-center text-sm text-gray-400">{t('notifications.empty')}</p>
      )}

      <div>
        {notifications.map((n) => (
          <NotificationItem key={n.id} notification={n} onRead={markRead} />
        ))}
      </div>
    </div>
  )
}
