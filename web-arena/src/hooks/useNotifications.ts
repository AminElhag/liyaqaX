import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getNotifications,
  getUnreadCount,
  markRead,
  markAllRead,
  notificationKeys,
} from '@/api/notifications'

export function useNotifications() {
  const queryClient = useQueryClient()

  const unreadCountQuery = useQuery({
    queryKey: notificationKeys.unreadCount(),
    queryFn: getUnreadCount,
    refetchInterval: 30_000,
  })

  const listQuery = useQuery({
    queryKey: notificationKeys.list(false),
    queryFn: () => getNotifications(false),
  })

  const markReadMutation = useMutation({
    mutationFn: markRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: notificationKeys.all })
    },
  })

  const markAllReadMutation = useMutation({
    mutationFn: markAllRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: notificationKeys.all })
    },
  })

  return {
    unreadCount: unreadCountQuery.data?.count ?? 0,
    notifications: listQuery.data ?? [],
    isLoading: listQuery.isLoading,
    markRead: markReadMutation.mutate,
    markAllRead: markAllReadMutation.mutate,
  }
}
