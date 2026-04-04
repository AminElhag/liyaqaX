import { Link, useMatches } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/cn'
import { useSidebarStore } from '@/stores/useSidebarStore'

interface NavItem {
  key: string
  to: string
  icon: string
}

const navItems: NavItem[] = [
  { key: 'dashboard', to: '/', icon: '◻' },
  { key: 'staff', to: '/staff', icon: '◈' },
  { key: 'members', to: '/members', icon: '◉' },
  { key: 'memberships', to: '/memberships', icon: '◎' },
  { key: 'finance', to: '/finance', icon: '◇' },
  { key: 'pt', to: '/pt', icon: '◆' },
  { key: 'gx', to: '/gx', icon: '○' },
  { key: 'leads', to: '/leads', icon: '◐' },
  { key: 'reports', to: '/reports', icon: '◑' },
  { key: 'settings', to: '/settings', icon: '◒' },
]

export function Sidebar() {
  const { t } = useTranslation()
  const { isCollapsed, toggle } = useSidebarStore()
  const matches = useMatches()
  const currentPath = matches[matches.length - 1]?.fullPath ?? '/'

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
          {navItems.map((item) => {
            const isActive =
              item.to === '/'
                ? currentPath === '/'
                : currentPath.startsWith(item.to)

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
                  {!isCollapsed && <span>{t(`nav.${item.key}`)}</span>}
                </Link>
              </li>
            )
          })}
        </ul>
      </nav>
    </aside>
  )
}
