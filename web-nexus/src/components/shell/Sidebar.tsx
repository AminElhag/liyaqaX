import { Link, useRouterState } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/cn'
import { useSidebarStore } from '@/stores/useSidebarStore'
import { useAuthStore } from '@/stores/useAuthStore'
import { hasPermission } from '@/lib/permissions'
import { Permission } from '@/types/permissions'
import type { PermissionCode } from '@/types/permissions'

interface NavItem {
  labelKey: string
  to: string
  permission: PermissionCode
}

const navItems: NavItem[] = [
  { labelKey: 'nav.home', to: '/', permission: Permission.PLATFORM_STATS_VIEW },
  { labelKey: 'nav.organizations', to: '/organizations', permission: Permission.ORGANIZATION_READ },
  { labelKey: 'nav.members', to: '/members', permission: Permission.MEMBER_READ },
  { labelKey: 'nav.roles', to: '/roles', permission: Permission.ROLE_READ },
  { labelKey: 'nav.audit', to: '/audit', permission: Permission.AUDIT_READ },
]

export function Sidebar() {
  const { t } = useTranslation()
  const { isCollapsed, toggle } = useSidebarStore()
  const permissions = useAuthStore((s) => s.permissions)
  const routerState = useRouterState()
  const currentPath = routerState.location.pathname

  return (
    <aside
      className={cn(
        'flex h-full flex-col border-e border-gray-200 bg-white transition-all duration-200',
        isCollapsed ? 'w-16' : 'w-56',
      )}
    >
      <div className="flex h-14 items-center justify-between border-b border-gray-200 px-3">
        {!isCollapsed && (
          <span className="text-lg font-bold text-blue-600">Nexus</span>
        )}
        <button
          type="button"
          onClick={toggle}
          className="rounded p-1.5 text-gray-500 hover:bg-gray-100"
          aria-label="Toggle sidebar"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 20 20"
            fill="currentColor"
            className="h-5 w-5"
          >
            <path
              fillRule="evenodd"
              d="M2 4.75A.75.75 0 012.75 4h14.5a.75.75 0 010 1.5H2.75A.75.75 0 012 4.75zM2 10a.75.75 0 01.75-.75h14.5a.75.75 0 010 1.5H2.75A.75.75 0 012 10zm0 5.25a.75.75 0 01.75-.75h14.5a.75.75 0 010 1.5H2.75a.75.75 0 01-.75-.75z"
              clipRule="evenodd"
            />
          </svg>
        </button>
      </div>

      <nav className="flex-1 space-y-1 px-2 py-3">
        {navItems
          .filter((item) => hasPermission(permissions, item.permission))
          .map((item) => {
            const isActive =
              item.to === '/'
                ? currentPath === '/'
                : currentPath.startsWith(item.to)

            return (
              <Link
                key={item.to}
                to={item.to}
                className={cn(
                  'flex items-center rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-gray-700 hover:bg-gray-100',
                  isCollapsed && 'justify-center px-2',
                )}
              >
                {!isCollapsed && t(item.labelKey)}
                {isCollapsed && (
                  <span className="text-xs">
                    {t(item.labelKey).charAt(0)}
                  </span>
                )}
              </Link>
            )
          })}
      </nav>
    </aside>
  )
}
