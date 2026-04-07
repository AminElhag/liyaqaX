import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useNotifications } from '@/hooks/useNotifications'
import { NotificationItem } from './NotificationItem'

interface NotificationDrawerProps {
  isOpen: boolean
  onClose: () => void
}

export function NotificationDrawer({ isOpen, onClose }: NotificationDrawerProps) {
  const { t } = useTranslation()
  const { notifications, fetchList, markRead, markAllRead, unreadCount } = useNotifications()

  useEffect(() => {
    if (isOpen) {
      fetchList()
    }
  }, [isOpen, fetchList])

  if (!isOpen) return null

  return (
    <>
      <div className="fixed inset-0 z-40" onClick={onClose} />
      <div className="fixed end-0 top-14 z-50 flex h-[calc(100vh-3.5rem)] w-96 flex-col border-s border-gray-200 bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-4 py-3">
          <h2 className="text-sm font-semibold text-gray-900">{t('notifications.title')}</h2>
          <div className="flex items-center gap-2">
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={() => markAllRead()}
                className="text-xs font-medium text-blue-600 hover:text-blue-700"
              >
                {t('notifications.markAllRead')}
              </button>
            )}
            <button
              type="button"
              onClick={onClose}
              className="rounded-md p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
            >
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="h-4 w-4">
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>
        <div className="flex-1 overflow-y-auto">
          {notifications.length === 0 && (
            <p className="py-12 text-center text-sm text-gray-400">
              {t('notifications.empty')}
            </p>
          )}
          {notifications.map((n) => (
            <NotificationItem key={n.id} notification={n} onRead={markRead} />
          ))}
        </div>
      </div>
    </>
  )
}
