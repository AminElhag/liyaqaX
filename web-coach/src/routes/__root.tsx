import { Outlet, createRootRouteWithContext, useNavigate } from '@tanstack/react-router'
import type { QueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/stores/useAuthStore'
import { useEffect } from 'react'
import { Sidebar } from '@/components/shell/Sidebar'
import { AppHeader } from '@/components/shell/AppHeader'

interface RouterContext {
  queryClient: QueryClient
}

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootComponent,
})

function RootComponent() {
  const { isAuthenticated, trainer } = useAuthStore()
  const navigate = useNavigate()

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

  if (isAuthRoute) {
    return <Outlet />
  }

  if (!isAuthenticated || !trainer) {
    return null
  }

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <AppHeader />
        <main className="flex-1 overflow-auto bg-gray-50 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
