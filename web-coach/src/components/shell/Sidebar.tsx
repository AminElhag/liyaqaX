import { Link, useLocation } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/useAuthStore'
import { cn } from '@/lib/cn'

const navItems = [
  { to: '/', labelKey: 'nav.schedule', requiredType: null },
  { to: '/pt', labelKey: 'nav.pt', requiredType: 'pt' },
  { to: '/gx', labelKey: 'nav.gx', requiredType: 'gx' },
  { to: '/profile', labelKey: 'nav.profile', requiredType: null },
] as const

export function Sidebar() {
  const { t } = useTranslation()
  const trainerTypes = useAuthStore((s) => s.trainerTypes)
  const location = useLocation()

  const visibleItems = navItems.filter(
    (item) => item.requiredType === null || trainerTypes.includes(item.requiredType),
  )

  return (
    <aside className="flex w-56 flex-col border-e border-gray-200 bg-white">
      <div className="flex h-14 items-center border-b border-gray-200 px-4">
        <span className="text-lg font-bold text-teal-700">Coach</span>
      </div>
      <nav className="flex-1 space-y-1 p-3">
        {visibleItems.map((item) => {
          const isActive =
            item.to === '/' ? location.pathname === '/' : location.pathname.startsWith(item.to)
          return (
            <Link
              key={item.to}
              to={item.to}
              className={cn(
                'block rounded-md px-3 py-2 text-sm font-medium',
                isActive
                  ? 'bg-teal-50 text-teal-700'
                  : 'text-gray-700 hover:bg-gray-100',
              )}
            >
              {t(item.labelKey)}
            </Link>
          )
        })}
      </nav>
    </aside>
  )
}
