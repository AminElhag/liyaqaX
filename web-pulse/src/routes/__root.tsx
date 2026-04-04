import { Outlet, createRootRouteWithContext, useNavigate } from '@tanstack/react-router'
import type { QueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/stores/useAuthStore'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { ALLOWED_SCOPES } from '@/types/permissions'
import { Sidebar } from '@/components/layout/Sidebar'
import { Topbar } from '@/components/layout/Topbar'
import { ErrorBoundary } from '@/components/layout/ErrorBoundary'

interface RouterContext {
  queryClient: QueryClient
}

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootComponent,
})

function RootComponent() {
  const { isAuthenticated, user } = useAuthStore()
  const navigate = useNavigate()
  const { t } = useTranslation()

  const isAuthRoute = window.location.pathname.startsWith('/auth')

  useEffect(() => {
    if (!isAuthenticated && !isAuthRoute) {
      const redirect = window.location.pathname + window.location.search
      navigate({
        to: '/auth/login',
        search: { redirect },
      })
    }
  }, [isAuthenticated, isAuthRoute, navigate])

  // Auth routes (login) — render without shell
  if (isAuthRoute) {
    return <Outlet />
  }

  // Not authenticated — show nothing while redirecting
  if (!isAuthenticated || !user) {
    return null
  }

  // Scope check — only club staff allowed
  if (!ALLOWED_SCOPES.includes(user.scope as (typeof ALLOWED_SCOPES)[number])) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50">
        <div className="max-w-md rounded-lg bg-white p-8 text-center shadow-md">
          <h1 className="mb-2 text-xl font-semibold text-red-600">
            {t('common.forbidden')}
          </h1>
          <p className="text-gray-600">{t('auth.login.scopeError')}</p>
        </div>
      </div>
    )
  }

  // Authenticated club user — render app shell
  return (
    <ErrorBoundary>
      <div className="flex h-screen overflow-hidden">
        <Sidebar />
        <div className="flex flex-1 flex-col overflow-hidden">
          <Topbar />
          <main className="flex-1 overflow-auto bg-gray-50">
            <Outlet />
          </main>
        </div>
      </div>
    </ErrorBoundary>
  )
}
