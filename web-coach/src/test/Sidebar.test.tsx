import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to }: { children: React.ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
  useLocation: () => ({ pathname: '/' }),
}))
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const map: Record<string, string> = {
        'nav.schedule': 'Schedule',
        'nav.pt': 'PT Sessions',
        'nav.gx': 'GX Classes',
        'nav.profile': 'Profile',
      }
      return map[key] ?? key
    },
  }),
}))

import { Sidebar } from '@/components/shell/Sidebar'
import { useAuthStore } from '@/stores/useAuthStore'

describe('Sidebar', () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth()
  })

  it('hides GX nav item for PT-only trainer', () => {
    useAuthStore.setState({ trainerTypes: ['pt'], isAuthenticated: true })
    render(<Sidebar />)

    expect(screen.getByText('Schedule')).toBeDefined()
    expect(screen.getByText('PT Sessions')).toBeDefined()
    expect(screen.getByText('Profile')).toBeDefined()
    expect(screen.queryByText('GX Classes')).toBeNull()
  })

  it('hides PT nav item for GX-only trainer', () => {
    useAuthStore.setState({ trainerTypes: ['gx'], isAuthenticated: true })
    render(<Sidebar />)

    expect(screen.getByText('Schedule')).toBeDefined()
    expect(screen.getByText('GX Classes')).toBeDefined()
    expect(screen.getByText('Profile')).toBeDefined()
    expect(screen.queryByText('PT Sessions')).toBeNull()
  })

  it('shows both nav items for dual trainer', () => {
    useAuthStore.setState({ trainerTypes: ['pt', 'gx'], isAuthenticated: true })
    render(<Sidebar />)

    expect(screen.getByText('PT Sessions')).toBeDefined()
    expect(screen.getByText('GX Classes')).toBeDefined()
  })
})
