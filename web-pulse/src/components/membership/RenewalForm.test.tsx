import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import { RenewalForm } from './RenewalForm'
import i18n from '@/i18n'

vi.mock('@/api/membershipPlans', () => ({
  getMembershipPlanList: vi.fn().mockResolvedValue({
    items: [
      {
        id: 'plan-1',
        nameAr: 'شهري أساسي',
        nameEn: 'Basic Monthly',
        priceHalalas: 15000,
        priceSar: '150.00',
        durationDays: 30,
        isActive: true,
      },
      {
        id: 'plan-2',
        nameAr: 'سنوي مميز',
        nameEn: 'Premium Annual',
        priceHalalas: 120000,
        priceSar: '1,200.00',
        durationDays: 365,
        isActive: true,
      },
    ],
    totalElements: 2,
    totalPages: 1,
    page: 0,
    size: 100,
  }),
  membershipPlanKeys: {
    all: ['membership-plans'],
    lists: () => ['membership-plans', 'list'],
    list: (params: Record<string, unknown>) => ['membership-plans', 'list', params],
  },
}))

vi.mock('@/api/memberships', () => ({
  renewMembership: vi.fn().mockResolvedValue({}),
  membershipKeys: {
    all: ['memberships'],
    active: (memberId: string) => ['memberships', 'active', memberId],
    histories: () => ['memberships', 'history'],
  },
}))

const defaultProps = {
  memberId: 'member-1',
  membershipId: 'ms-1',
  currentEndDate: '2026-04-30',
  onSuccess: vi.fn(),
  onCancel: vi.fn(),
}

describe('RenewalForm', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
    vi.clearAllMocks()
  })

  it('renders plan selector and payment method', async () => {
    renderWithProviders(<RenewalForm {...defaultProps} />)

    await waitFor(() => {
      expect(screen.getByText('Basic Monthly', { exact: false })).toBeInTheDocument()
    })

    const selects = screen.getAllByRole('combobox')
    expect(selects.length).toBe(2)

    expect(screen.getAllByText('Select a plan').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('Payment method')).toBeInTheDocument()
  })

  it('renders plan options after loading', async () => {
    renderWithProviders(<RenewalForm {...defaultProps} />)

    await waitFor(() => {
      expect(screen.getByText(/Basic Monthly/)).toBeInTheDocument()
    })

    expect(screen.getByText(/Premium Annual/)).toBeInTheDocument()
  })

  it('has submit button disabled when no plan is selected', async () => {
    renderWithProviders(<RenewalForm {...defaultProps} />)

    await waitFor(() => {
      expect(screen.getByText(/Basic Monthly/)).toBeInTheDocument()
    })

    const submitButton = screen.getByRole('button', { name: /renew/i })
    expect(submitButton).toBeDisabled()
  })

  it('enables submit button when a plan is selected', async () => {
    const user = userEvent.setup()
    renderWithProviders(<RenewalForm {...defaultProps} />)

    await waitFor(() => {
      expect(screen.getByText(/Basic Monthly/)).toBeInTheDocument()
    })

    const planSelect = screen.getAllByRole('combobox')[0]
    await user.selectOptions(planSelect, 'plan-1')

    await waitFor(() => {
      const submitButton = screen.getByRole('button', { name: /renew/i })
      expect(submitButton).not.toBeDisabled()
    })
  })

  it('renders cancel button that calls onCancel', async () => {
    const user = userEvent.setup()
    renderWithProviders(<RenewalForm {...defaultProps} />)

    const cancelButton = screen.getByRole('button', { name: /cancel/i })
    await user.click(cancelButton)

    expect(defaultProps.onCancel).toHaveBeenCalledOnce()
  })

  it('shows the computed start date as read-only', async () => {
    renderWithProviders(<RenewalForm {...defaultProps} />)

    const dateInput = screen.getByDisplayValue('2026-05-01')
    expect(dateInput).toHaveAttribute('readOnly')
  })
})
