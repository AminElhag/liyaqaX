import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import { AssignPlanForm } from './AssignPlanForm'
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
        nameAr: 'ربع سنوي',
        nameEn: 'Quarterly Standard',
        priceHalalas: 39900,
        priceSar: '399.00',
        durationDays: 90,
        isActive: true,
      },
      {
        id: 'plan-3',
        nameAr: 'غير نشط',
        nameEn: 'Inactive Plan',
        priceHalalas: 10000,
        priceSar: '100.00',
        durationDays: 30,
        isActive: false,
      },
    ],
    pagination: { page: 0, size: 100, totalElements: 3, totalPages: 1, hasNext: false, hasPrevious: false },
  }),
  membershipPlanKeys: {
    all: ['membership-plans'],
    lists: () => ['membership-plans', 'list'],
    list: (params: Record<string, unknown>) => ['membership-plans', 'list', params],
    details: () => ['membership-plans', 'detail'],
    detail: (id: string) => ['membership-plans', 'detail', id],
  },
}))

describe('AssignPlanForm', () => {
  const onSuccess = vi.fn()
  const onCancel = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    i18n.changeLanguage('en')
  })

  it('renders plan dropdown', async () => {
    renderWithProviders(
      <AssignPlanForm memberId="m-1" onSuccess={onSuccess} onCancel={onCancel} />,
    )
    await waitFor(() => {
      expect(screen.getByText(/Basic Monthly/)).toBeInTheDocument()
    })
  })

  it('does not show inactive plans in dropdown', async () => {
    renderWithProviders(
      <AssignPlanForm memberId="m-1" onSuccess={onSuccess} onCancel={onCancel} />,
    )
    await waitFor(() => {
      expect(screen.queryByText(/Inactive Plan/)).not.toBeInTheDocument()
    })
  })

  it('shows amount when plan is selected', async () => {
    const user = userEvent.setup()
    renderWithProviders(
      <AssignPlanForm memberId="m-1" onSuccess={onSuccess} onCancel={onCancel} />,
    )
    await waitFor(() => {
      expect(screen.getByText(/Basic Monthly/)).toBeInTheDocument()
    })

    const select = screen.getAllByRole('combobox')[0]
    await user.selectOptions(select, 'plan-1')

    expect(screen.getByText('30 days')).toBeInTheDocument()
  })

  it('calls onCancel when cancel button clicked', async () => {
    const user = userEvent.setup()
    renderWithProviders(
      <AssignPlanForm memberId="m-1" onSuccess={onSuccess} onCancel={onCancel} />,
    )

    await user.click(screen.getByText('Cancel'))
    expect(onCancel).toHaveBeenCalled()
  })

  it('submit button is disabled without a plan selected', async () => {
    renderWithProviders(
      <AssignPlanForm memberId="m-1" onSuccess={onSuccess} onCancel={onCancel} />,
    )
    await waitFor(() => {
      const submitBtn = screen.getByText('Assign plan & collect payment')
      expect(submitBtn).toBeDisabled()
    })
  })
})
