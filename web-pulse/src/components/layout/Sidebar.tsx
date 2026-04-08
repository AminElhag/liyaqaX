import { Link, useMatches } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { cn } from '@/lib/cn'
import { useSidebarStore } from '@/stores/useSidebarStore'
import { useAuthStore } from '@/stores/useAuthStore'
import { hasPermission } from '@/lib/permissions'
import { Permission } from '@/types/permissions'
import { getPendingMembers, memberKeys } from '@/api/members'
import { getFollowUps, memberNoteKeys } from '@/api/memberNotes'
import { getLapsedMembers, lapsedKeys } from '@/api/memberLapse'

interface NavItem {
  key: string
  to: string
  icon: string
}

const navItems: NavItem[] = [
  { key: 'dashboard', to: '/', icon: '◻' },
  { key: 'check_in', to: '/check-in', icon: '◫' },
  { key: 'staff', to: '/staff', icon: '◈' },
  { key: 'members', to: '/members', icon: '◉' },
  { key: 'memberships', to: '/memberships', icon: '◎' },
  { key: 'lapsed', to: '/memberships/lapsed', icon: '◌' },
  { key: 'finance', to: '/finance', icon: '◇' },
  { key: 'pt', to: '/pt', icon: '◆' },
  { key: 'gx', to: '/gx', icon: '○' },
  { key: 'leads', to: '/leads', icon: '◐' },
  { key: 'follow_ups', to: '/follow-ups', icon: '◔' },
  { key: 'schedule', to: '/schedule', icon: '◫' },
  { key: 'my_shifts', to: '/my-shifts', icon: '◰' },
  { key: 'cash_drawer', to: '/cash-drawer', icon: '◧' },
  { key: 'reports', to: '/reports', icon: '◑' },
  { key: 'settings', to: '/settings', icon: '◒' },
]

export function Sidebar() {
  const { t } = useTranslation()
  const { isCollapsed, toggle } = useSidebarStore()
  const matches = useMatches()
  const currentPath = matches[matches.length - 1]?.fullPath ?? '/'

  const permissions = useAuthStore((s) => s.permissions)
  const canSeeFollowUps = hasPermission(permissions, Permission.MEMBER_NOTE_FOLLOW_UP_READ)
  const canSeeCheckIn = hasPermission(permissions, Permission.CHECK_IN_READ)
  const canSeeSchedule = hasPermission(permissions, Permission.SHIFT_MANAGE)
  const canSeeMyShifts = hasPermission(permissions, Permission.SHIFT_READ)

  const { data: pendingData } = useQuery({
    queryKey: memberKeys.pendingCount(),
    queryFn: () => getPendingMembers({ page: 0, size: 1 }),
    refetchInterval: 60_000,
  })
  const pendingCount = pendingData?.pagination.totalElements ?? 0

  const { data: followUpData } = useQuery({
    queryKey: memberNoteKeys.followUps(),
    queryFn: getFollowUps,
    enabled: canSeeFollowUps,
    refetchInterval: 60_000,
  })
  const followUpTodayCount = followUpData?.followUps.filter((f) => f.daysUntilDue <= 0).length ?? 0

  const { data: lapsedData } = useQuery({
    queryKey: lapsedKeys.count(),
    queryFn: () => getLapsedMembers(1, 1),
    refetchInterval: 120_000,
  })
  const lapsedCount = lapsedData?.total ?? 0

  return (
    <aside
      className={cn(
        'flex h-screen flex-col border-e border-gray-200 bg-white transition-all duration-200',
        isCollapsed ? 'w-16' : 'w-56',
      )}
    >
      <div className="flex h-14 items-center justify-between border-b border-gray-200 px-3">
        {!isCollapsed && (
          <span className="text-lg font-bold text-gray-900">Pulse</span>
        )}
        <button
          type="button"
          onClick={toggle}
          className="rounded p-1 text-gray-500 hover:bg-gray-100 hover:text-gray-700"
          aria-label="Toggle sidebar"
        >
          {isCollapsed ? '▸' : '◂'}
        </button>
      </div>

      <nav className="flex-1 overflow-y-auto p-2">
        <ul className="space-y-1">
          {navItems
            .filter((item) => {
              if (item.key === 'follow_ups') return canSeeFollowUps
              if (item.key === 'check_in') return canSeeCheckIn
              if (item.key === 'schedule') return canSeeSchedule
              if (item.key === 'my_shifts') return canSeeMyShifts
              return true
            })
            .map((item) => {
              const isActive =
                item.to === '/'
                  ? currentPath === '/'
                  : currentPath.startsWith(item.to)

              const badgeCount =
                item.key === 'members'
                  ? pendingCount
                  : item.key === 'follow_ups'
                    ? followUpTodayCount
                    : item.key === 'lapsed'
                      ? lapsedCount
                      : 0

              return (
                <li key={item.key}>
                  <Link
                    to={item.to}
                    className={cn(
                      'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                      isActive
                        ? 'bg-blue-50 text-blue-700'
                        : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900',
                      isCollapsed && 'justify-center px-2',
                    )}
                    title={isCollapsed ? t(`nav.${item.key}`) : undefined}
                  >
                    <span className="text-base">{item.icon}</span>
                    {!isCollapsed && (
                      <span className="flex flex-1 items-center justify-between">
                        <span>{t(`nav.${item.key}`)}</span>
                        {badgeCount > 0 && (
                          <span className="rounded-full bg-red-500 px-1.5 py-0.5 text-xs font-bold text-white">
                            {badgeCount}
                          </span>
                        )}
                      </span>
                    )}
                  </Link>
                </li>
              )
            })}
        </ul>
      </nav>
    </aside>
  )
}
