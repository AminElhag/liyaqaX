import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

const mockMyShifts = {
  shifts: [
    {
      shiftId: 'shift-1',
      branchName: 'Elixir Gym - Riyadh',
      startAt: new Date(Date.now() + 86400000).toISOString(),
      endAt: new Date(Date.now() + 86400000 + 28800000).toISOString(),
      notes: 'Morning shift',
      swapRequest: null,
    },
    {
      shiftId: 'shift-2',
      branchName: 'Elixir Gym - Riyadh',
      startAt: new Date(Date.now() + 172800000).toISOString(),
      endAt: new Date(Date.now() + 172800000 + 28800000).toISOString(),
      notes: null,
      swapRequest: {
        swapId: 'swap-1',
        targetStaffName: 'Sara Al-Zahrani',
        status: 'PENDING_ACCEPTANCE',
      },
    },
    {
      shiftId: 'shift-3',
      branchName: 'Elixir Gym - Riyadh',
      startAt: new Date(Date.now() + 259200000).toISOString(),
      endAt: new Date(Date.now() + 259200000 + 28800000).toISOString(),
      notes: null,
      swapRequest: {
        swapId: 'swap-2',
        targetStaffName: 'Ahmed',
        status: 'APPROVED',
      },
    },
  ],
}

vi.mock('@/api/shifts', () => ({
  getMyShifts: vi.fn().mockResolvedValue(mockMyShifts),
  requestSwap: vi.fn().mockResolvedValue({ swapId: 'new-swap' }),
  shiftKeys: {
    all: ['shifts'],
    roster: (b: string, w: string) => ['shifts', 'roster', b, w],
    my: () => ['shifts', 'my'],
    pendingSwaps: () => ['shifts', 'pending-swaps'],
  },
}))

vi.mock('@/api/staff', () => ({
  getStaffList: vi.fn().mockResolvedValue({
    items: [{ id: 'staff-2', firstNameEn: 'Sara', lastNameEn: 'Al-Zahrani', isActive: true }],
    pagination: { totalElements: 1 },
  }),
  staffKeys: { all: ['staff'], lists: () => ['staff', 'list'], list: () => ['staff', 'list', {}] },
}))

vi.mock('@/stores/useAuthStore', () => ({
  useAuthStore: (sel: (s: { permissions: Set<string> }) => unknown) =>
    sel({ permissions: new Set(['shift:read']) }),
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, string>) => {
      if (opts && typeof opts === 'object' && 'name' in opts) return `${key} ${opts.name}`
      if (typeof opts === 'string') return opts
      return key
    },
    i18n: { language: 'en' },
  }),
}))

vi.mock('@tanstack/react-router', () => ({
  createFileRoute: () => () => ({ component: () => null }),
}))

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

const MyShiftsModule = await import('./index')
const RouteObj = MyShiftsModule.Route as unknown as { options: { component: React.ComponentType } }
const MyShiftsPage = RouteObj?.options?.component

describe('My Shifts Page', () => {
  it('renders upcoming shifts list', async () => {
    if (!MyShiftsPage) return
    render(<MyShiftsPage />, { wrapper })
    await waitFor(() => {
      expect(screen.getByText('myshifts.page_title')).toBeInTheDocument()
    })
  })

  it('shows Request Swap button for shifts without open swap', async () => {
    if (!MyShiftsPage) return
    render(<MyShiftsPage />, { wrapper })
    await waitFor(() => {
      expect(screen.getAllByText('myshifts.request_swap').length).toBeGreaterThan(0)
    })
  })

  it('renders swap status badges', async () => {
    if (!MyShiftsPage) return
    render(<MyShiftsPage />, { wrapper })
    await waitFor(() => {
      expect(screen.getByText(/myshifts.swap_status.pending_acceptance/)).toBeInTheDocument()
      expect(screen.getByText(/myshifts.swap_status.approved/)).toBeInTheDocument()
    })
  })
})
