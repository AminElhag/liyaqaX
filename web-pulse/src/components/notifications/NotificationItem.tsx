import { useTranslation } from 'react-i18next'
import { formatDistanceToNow } from 'date-fns'
import type { NotificationResponse } from '@/api/notifications'
import { cn } from '@/lib/cn'

interface NotificationItemProps {
  notification: NotificationResponse
  onRead: (id: string) => void
}

export function NotificationItem({ notification, onRead }: NotificationItemProps) {
  const { t } = useTranslation()
  const isUnread = notification.readAt === null

  const title = t(notification.titleKey, notification.params ?? {})
  const body = t(notification.bodyKey, notification.params ?? {})
  const timeAgo = formatDistanceToNow(new Date(notification.createdAt), { addSuffix: true })

  return (
    <button
      type="button"
      onClick={() => {
        if (isUnread) onRead(notification.id)
      }}
      className={cn(
        'flex w-full gap-3 border-b border-gray-100 px-4 py-3 text-start hover:bg-gray-50',
        isUnread && 'bg-blue-50/50',
      )}
    >
      {isUnread && (
        <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-blue-500" />
      )}
      {!isUnread && <span className="mt-1.5 h-2 w-2 shrink-0" />}
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-gray-900">{title}</p>
        <p className="mt-0.5 text-xs text-gray-600">{body}</p>
        <p className="mt-1 text-xs text-gray-400">{timeAgo}</p>
      </div>
    </button>
  )
}
