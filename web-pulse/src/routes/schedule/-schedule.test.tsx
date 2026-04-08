import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

const mockRoster = {
  branchName: 'Test Branch',
  weekStart: '2026-04-13',
  weekEnd: '2026-04-19',
  shifts: [
    {
      shiftId: 'shift-1',
      staffMemberId: 'staff-1',
      staffMemberName: 'Khalid Al-Otaibi',
      startAt: '2026-04-14T06:00:00Z',
      endAt: '2026-04-14T14:00:00Z',
      notes: 'Opening shift',
      hasPendingSwap: false,
    },
    {
      shiftId: 'shift-2',
      staffMemberId: 'staff-1',
      staffMemberName: 'Khalid Al-Otaibi',
      startAt: '2026-04-15T06:00:00Z',
      endAt: '2026-04-15T14:00:00Z',
      notes: null,
      hasPendingSwap: true,
    },
  ],
}

const mockPending = {
  swapRequests: [
    {
      swapId: 'swap-1',
      shiftDate: '2026-04-14',
      shiftStart: '2026-04-14T06:00:00Z',
      shiftEnd: '2026-04-14T14:00:00Z',
      requesterName: 'Khalid',
      targetName: 'Sara',
      status: 'PENDING_APPROVAL',
      requesterNote: 'Doctor appointment',
    },
  ],
}

vi.mock('@/api/shifts', () => ({
  getRoster: vi.fn().mockResolvedValue(mockRoster),
  getPendingSwaps: vi.fn().mockResolvedValue(mockPending),
  resolveSwap: vi.fn().mockResolvedValue(undefined),
  deleteShift: vi.fn().mockResolvedValue(undefined),
  createShift: vi.fn().mockResolvedValue({ shiftId: 'new-1' }),
  shiftKeys: {
    all: ['shifts'],
    roster: (b: string, w: string) => ['shifts', 'roster', b, w],
    my: () => ['shifts', 'my'],
    pendingSwaps: () => ['shifts', 'pending-swaps'],
  },
}))

vi.mock('@/api/staff', () => ({
  getStaffList: vi.fn().mockResolvedValue({
    items: [{ id: 'staff-1', firstNameEn: 'Khalid', lastNameEn: 'Al-Otaibi', isActive: true }],
    pagination: { totalElements: 1 },
  }),
  staffKeys: { all: ['staff'], lists: () => ['staff', 'list'], list: () => ['staff', 'list', {}] },
}))

vi.mock('@/stores/useBranchStore', () => ({
  useBranchStore: (sel: (s: { activeBranch: { id: string } }) => unknown) =>
    sel({ activeBranch: { id: 'branch-1' } }),
}))

vi.mock('@/stores/useAuthStore', () => ({
  useAuthStore: (sel: (s: { permissions: Set<string> }) => unknown) =>
    sel({ permissions: new Set(['shift:manage', 'shift:read']) }),
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
  Link: ({ children, ...props }: Record<string, unknown>) => <a {...props}>{children as React.ReactNode}</a>,
  useMatches: () => [{ fullPath: '/schedule' }],
}))

// Must import after mocks
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

function wrapper({ children }: { children: React.ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}

// Dynamic import to avoid hoisting
const ScheduleModule = await import('./index')
const RouteObj = ScheduleModule.Route as unknown as { options: { component: React.ComponentType } }
const SchedulePage = RouteObj?.options?.component

describe('Schedule Page', () => {
  it('renders weekly roster grid with shifts', async () => {
    if (!SchedulePage) return
    render(<SchedulePage />, { wrapper })
    await waitFor(() => {
      expect(screen.getByText('schedule.page_title')).toBeInTheDocument()
    })
  })

  it('renders pending swap panel with approve/reject actions', async () => {
    if (!SchedulePage) return
    render(<SchedulePage />, { wrapper })
    await waitFor(() => {
      expect(screen.getByText('schedule.pending_swaps_title')).toBeInTheDocument()
    })
    expect(screen.getByText('schedule.swap_approve')).toBeInTheDocument()
    expect(screen.getByText('schedule.swap_reject')).toBeInTheDocument()
  })

  it('shows Add Shift button', async () => {
    if (!SchedulePage) return
    render(<SchedulePage />, { wrapper })
    await waitFor(() => {
      expect(screen.getByText('schedule.add_shift')).toBeInTheDocument()
    })
  })

  it('Add Shift modal opens on button click', async () => {
    if (!SchedulePage) return
    render(<SchedulePage />, { wrapper })
    await waitFor(() => {
      expect(screen.getByText('schedule.add_shift')).toBeInTheDocument()
    })
    await userEvent.click(screen.getByText('schedule.add_shift'))
    await waitFor(() => {
      expect(screen.getByText('schedule.shift_modal_title')).toBeInTheDocument()
    })
  })
})
