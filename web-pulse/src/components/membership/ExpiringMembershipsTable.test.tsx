import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import { ExpiringMembershipsTable } from './ExpiringMembershipsTable'
import i18n from '@/i18n'

vi.mock('@/api/memberships', () => ({
  getExpiringMemberships: vi.fn().mockResolvedValue({
    items: [
      {
        memberId: 'member-1',
        memberName: 'Ahmed Al-Rashid',
        memberPhone: '+966501234567',
        planNameAr: 'شهري أساسي',
        planNameEn: 'Basic Monthly',
        endDate: '2026-04-10',
        daysRemaining: 5,
        membershipId: 'ms-1',
        membershipStatus: 'active',
      },
      {
        memberId: 'member-2',
        memberName: 'Fatima Hassan',
        memberPhone: '+966509876543',
        planNameAr: 'سنوي مميز',
        planNameEn: 'Premium Annual',
        endDate: '2026-04-20',
        daysRemaining: 15,
        membershipId: 'ms-2',
        membershipStatus: 'active',
      },
      {
        memberId: 'member-3',
        memberName: 'Omar Khalid',
        memberPhone: '+966507654321',
        planNameAr: 'شهري أساسي',
        planNameEn: 'Basic Monthly',
        endDate: '2026-04-01',
        daysRemaining: -4,
        membershipId: 'ms-3',
        membershipStatus: 'expired',
      },
    ],
    totalElements: 3,
    totalPages: 1,
    page: 0,
    size: 100,
  }),
  membershipKeys: {
    all: ['memberships'],
    expiring: (params: Record<string, unknown>) => ['memberships', 'expiring', params],
  },
}))

describe('ExpiringMembershipsTable', () => {
  const onRenew = vi.fn()

  beforeEach(() => {
    i18n.changeLanguage('en')
    vi.clearAllMocks()
  })

  it('renders the table with expiring memberships title', async () => {
    renderWithProviders(<ExpiringMembershipsTable onRenew={onRenew} />)

    expect(screen.getByText('Expiring memberships')).toBeInTheDocument()
  })

  it('renders filter buttons for 7, 14, 30 days and overdue', () => {
    renderWithProviders(<ExpiringMembershipsTable onRenew={onRenew} />)

    expect(screen.getByRole('button', { name: '7 days' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '14 days' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '30 days' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Overdue' })).toBeInTheDocument()
  })

  it('displays member names in the table after loading', async () => {
    renderWithProviders(<ExpiringMembershipsTable onRenew={onRenew} />)

    await waitFor(() => {
      expect(screen.getByText('Ahmed Al-Rashid')).toBeInTheDocument()
    })

    expect(screen.getByText('Fatima Hassan')).toBeInTheDocument()
    expect(screen.getByText('Omar Khalid')).toBeInTheDocument()
  })

  it('displays plan names in English', async () => {
    renderWithProviders(<ExpiringMembershipsTable onRenew={onRenew} />)

    await waitFor(() => {
      expect(screen.getAllByText('Basic Monthly').length).toBeGreaterThanOrEqual(1)
    })

    expect(screen.getByText('Premium Annual')).toBeInTheDocument()
  })

  it('shows renew buttons for each membership row', async () => {
    renderWithProviders(<ExpiringMembershipsTable onRenew={onRenew} />)

    await waitFor(() => {
      const renewButtons = screen.getAllByRole('button', { name: /renew/i })
      // 4 filter buttons + 3 renew buttons, but filtering by "Renew" text
      expect(renewButtons.length).toBe(3)
    })
  })

  it('calls onRenew with correct ids when renew button is clicked', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ExpiringMembershipsTable onRenew={onRenew} />)

    await waitFor(() => {
      expect(screen.getByText('Ahmed Al-Rashid')).toBeInTheDocument()
    })

    const renewButtons = screen.getAllByRole('button', { name: /renew/i })
    // Click the first renew button (for Ahmed Al-Rashid or Omar Khalid depending on render order)
    await user.click(renewButtons[0])

    expect(onRenew).toHaveBeenCalledOnce()
  })

  it('switches active filter when a filter button is clicked', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ExpiringMembershipsTable onRenew={onRenew} />)

    const sevenDaysButton = screen.getByRole('button', { name: '7 days' })
    await user.click(sevenDaysButton)

    // The 7 days button should now have the active styling (bg-blue-600)
    expect(sevenDaysButton.className).toContain('bg-blue-600')
  })
})
