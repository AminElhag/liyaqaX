import { apiClient } from './client'

export interface NotificationResponse {
  id: string
  type: string
  titleKey: string
  bodyKey: string
  params: Record<string, string | number> | null
  entityType: string | null
  entityId: string | null
  readAt: string | null
  createdAt: string
}

export interface UnreadCountResponse {
  count: number
}

export const notificationKeys = {
  all: ['notifications'] as const,
  list: (unreadOnly: boolean) => [...notificationKeys.all, 'list', unreadOnly] as const,
  unreadCount: () => [...notificationKeys.all, 'unread-count'] as const,
}

export async function getNotifications(unreadOnly = false): Promise<NotificationResponse[]> {
  const { data } = await apiClient.get<NotificationResponse[]>('/notifications', {
    params: { unreadOnly, size: 20 },
  })
  return data
}

export async function getUnreadCount(): Promise<UnreadCountResponse> {
  const { data } = await apiClient.get<UnreadCountResponse>('/notifications/unread-count')
  return data
}

export async function markRead(id: string): Promise<void> {
  await apiClient.patch(`/notifications/${id}/read`)
}

export async function markAllRead(): Promise<void> {
  await apiClient.patch('/notifications/read-all')
}
