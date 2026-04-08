import { Outlet, createRootRouteWithContext, useNavigate } from '@tanstack/react-router'
import type { QueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/stores/useAuthStore'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { NotificationBell } from '@/components/notifications/NotificationBell'
import { LapsedBanner } from '@/components/LapsedBanner'

interface RouterContext {
  queryClient: QueryClient
}

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootComponent,
})

function RootComponent() {
  const { isAuthenticated, member } = useAuthStore()
  const navigate = useNavigate()
  const { i18n } = useTranslation()

  const isAuthRoute = window.location.pathname.startsWith('/auth')

  useEffect(() => {
    if (!isAuthenticated && !isAuthRoute) {
      navigate({ to: '/auth/login' })
    }
  }, [isAuthenticated, isAuthRoute, navigate])

  useEffect(() => {
    if (isAuthenticated && member?.preferredLanguage) {
      i18n.changeLanguage(member.preferredLanguage)
    }
  }, [isAuthenticated, member?.preferredLanguage, i18n])

  if (isAuthRoute) {
    return <Outlet />
  }

  if (!isAuthenticated) {
    return null
  }

  // Language guard — redirect to language selection if no preference set
  if (member && !member.preferredLanguage && !window.location.pathname.startsWith('/auth/language')) {
    navigate({ to: '/auth/language' })
    return null
  }

  const isLapsed = member?.memberStatus === 'lapsed'

  if (isLapsed) {
    return <LapsedBanner />
  }

  return (
    <div className="flex min-h-screen flex-col bg-gray-50">
      <header className="sticky top-0 z-10 border-b bg-white px-4 py-3">
        <div className="flex items-center justify-between">
          <span className="text-sm font-medium text-gray-500">
            {member?.club?.nameAr || member?.club?.name}
          </span>
          <div className="flex items-center gap-2">
            <NotificationBell />
            <span className="text-sm font-semibold">
              {member?.firstName} {member?.lastName}
            </span>
          </div>
        </div>
      </header>
      <main className="flex-1 px-4 py-4">
        <Outlet />
      </main>
      <BottomNav />
    </div>
  )
}

function BottomNav() {
  const { portalSettings } = useAuthStore()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const path = window.location.pathname

  const items = [
    { label: t('nav.home'), path: '/', show: true },
    { label: t('nav.gx'), path: '/classes', show: portalSettings?.gxBookingEnabled ?? true },
    { label: t('nav.pt'), path: '/sessions', show: portalSettings?.ptViewEnabled ?? true },
    { label: t('nav.invoices'), path: '/payments/invoices', show: portalSettings?.invoiceViewEnabled ?? true },
    { label: t('nav.profile'), path: '/account', show: true },
  ]

  return (
    <nav className="sticky bottom-0 z-10 border-t bg-white">
      <div className="flex justify-around py-2">
        {items.filter(i => i.show).map((item) => (
          <button
            key={item.path}
            onClick={() => navigate({ to: item.path })}
            className={`flex flex-col items-center px-3 py-1 text-xs ${
              path === item.path ? 'font-semibold text-blue-600' : 'text-gray-500'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>
    </nav>
  )
}
