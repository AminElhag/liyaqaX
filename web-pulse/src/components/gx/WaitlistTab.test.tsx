import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        'common.loading': 'Loading...',
        'gx.waitlist.empty_list': 'No one on the waitlist',
        'gx.waitlist.col_position': 'Position',
        'gx.waitlist.col_member': 'Member',
        'gx.waitlist.col_phone': 'Phone',
        'gx.waitlist.col_status': 'Status',
        'gx.waitlist.col_expires': 'Offer Expires',
        'gx.waitlist.remove': 'Remove',
        'common.confirm': 'Confirm',
        'common.cancel': 'Cancel',
      }
      return translations[key] ?? key
    },
  }),
}))

vi.mock('@/api/gx', () => ({
  getWaitlistEntries: vi.fn(),
  removeWaitlistEntry: vi.fn(),
  gxKeys: {
    all: ['gx'],
    waitlistEntries: (id: string) => ['gx', 'instances', 'detail', id, 'waitlist-entries'],
    instanceDetail: (id: string) => ['gx', 'instances', 'detail', id],
  },
}))

import { WaitlistTab } from './WaitlistTab'
import { getWaitlistEntries } from '@/api/gx'

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
}

describe('WaitlistTab', () => {
  it('renders waitlist entries when data is loaded', async () => {
    vi.mocked(getWaitlistEntries).mockResolvedValue({
      waitlistCount: 1,
      entries: [
        {
          entryId: 'e1',
          position: 1,
          status: 'WAITING',
          memberName: 'Ahmed Al-Rashidi',
          memberPhone: '+966501234567',
          notifiedAt: null,
          offerExpiresAt: null,
          createdAt: '2026-04-07T10:00:00Z',
        },
      ],
    })

    render(<WaitlistTab classId="class-1" />, { wrapper: createWrapper() })

    expect(await screen.findByText('Ahmed Al-Rashidi')).toBeInTheDocument()
    expect(screen.getByText('#1')).toBeInTheDocument()
    expect(screen.getByText('Remove')).toBeInTheDocument()
  })

  it('shows empty message when no entries', async () => {
    vi.mocked(getWaitlistEntries).mockResolvedValue({
      waitlistCount: 0,
      entries: [],
    })

    render(<WaitlistTab classId="class-1" />, { wrapper: createWrapper() })

    expect(await screen.findByText('No one on the waitlist')).toBeInTheDocument()
  })
})
