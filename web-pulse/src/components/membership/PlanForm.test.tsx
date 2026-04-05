import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import { PlanForm } from './PlanForm'
import i18n from '@/i18n'
import type { MembershipPlan } from '@/types/domain'

const existingPlan: MembershipPlan = {
  id: 'plan-1',
  organizationId: 'org-1',
  clubId: 'club-1',
  nameAr: 'شهري أساسي',
  nameEn: 'Basic Monthly',
  descriptionAr: null,
  descriptionEn: null,
  priceHalalas: 15000,
  priceSar: '150.00',
  durationDays: 30,
  gracePeriodDays: 3,
  freezeAllowed: true,
  maxFreezeDays: 14,
  gxClassesIncluded: true,
  ptSessionsIncluded: false,
  isActive: true,
  sortOrder: 1,
  createdAt: '2025-01-01T00:00:00Z',
  updatedAt: '2025-01-01T00:00:00Z',
}

describe('PlanForm', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
  })

  it('renders all required fields for create mode', () => {
    renderWithProviders(
      <PlanForm
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
        isSubmitting={false}
      />,
    )

    expect(screen.getByLabelText('Plan name (Arabic)')).toBeInTheDocument()
    expect(screen.getByLabelText('Plan name (English)')).toBeInTheDocument()
    expect(screen.getByLabelText('Price (SAR)')).toBeInTheDocument()
    expect(screen.getByLabelText('Duration (days)')).toBeInTheDocument()
    expect(screen.getByLabelText('Grace period (days)')).toBeInTheDocument()
    expect(screen.getByLabelText('Allow freeze')).toBeInTheDocument()
    expect(screen.getByLabelText('Include GX classes')).toBeInTheDocument()
    expect(screen.getByLabelText('Include PT sessions')).toBeInTheDocument()
  })

  it('shows Add plan button in create mode', () => {
    renderWithProviders(
      <PlanForm
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
        isSubmitting={false}
      />,
    )

    expect(screen.getByRole('button', { name: 'Add plan' })).toBeInTheDocument()
  })

  it('shows Edit plan button in edit mode', () => {
    renderWithProviders(
      <PlanForm
        plan={existingPlan}
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
        isSubmitting={false}
      />,
    )

    expect(screen.getByRole('button', { name: 'Edit plan' })).toBeInTheDocument()
  })

  it('pre-fills fields in edit mode', () => {
    renderWithProviders(
      <PlanForm
        plan={existingPlan}
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
        isSubmitting={false}
      />,
    )

    expect(screen.getByLabelText('Plan name (English)')).toHaveValue('Basic Monthly')
    expect(screen.getByLabelText('Plan name (Arabic)')).toHaveValue('شهري أساسي')
    expect(screen.getByLabelText('Price (SAR)')).toHaveValue(150)
    expect(screen.getByLabelText('Duration (days)')).toHaveValue(30)
    expect(screen.getByLabelText('Grace period (days)')).toHaveValue(3)
  })

  it('converts SAR to halalas on submit', async () => {
    const onSubmit = vi.fn()
    const user = userEvent.setup()

    renderWithProviders(
      <PlanForm
        onSubmit={onSubmit}
        onCancel={vi.fn()}
        isSubmitting={false}
      />,
    )

    await user.type(screen.getByLabelText('Plan name (Arabic)'), 'خطة')
    await user.type(screen.getByLabelText('Plan name (English)'), 'Test Plan')
    await user.type(screen.getByLabelText('Price (SAR)'), '150')
    await user.type(screen.getByLabelText('Duration (days)'), '30')

    await user.click(screen.getByRole('button', { name: 'Add plan' }))

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledTimes(1)
    })

    const submittedData = onSubmit.mock.calls[0][0]
    expect(submittedData.priceHalalas).toBe(15000)
  })

  it('calls onCancel when cancel button is clicked', async () => {
    const onCancel = vi.fn()
    const user = userEvent.setup()

    renderWithProviders(
      <PlanForm
        onSubmit={vi.fn()}
        onCancel={onCancel}
        isSubmitting={false}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('disables submit button when isSubmitting is true', () => {
    renderWithProviders(
      <PlanForm
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
        isSubmitting={true}
      />,
    )

    expect(screen.getByRole('button', { name: 'Saving...' })).toBeDisabled()
  })

  it('hides max freeze days field when freeze is not allowed', async () => {
    const user = userEvent.setup()

    renderWithProviders(
      <PlanForm
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
        isSubmitting={false}
      />,
    )

    const freezeCheckbox = screen.getByLabelText('Allow freeze')
    expect(freezeCheckbox).toBeChecked()
    expect(screen.getByLabelText('Max freeze days per year')).toBeInTheDocument()

    await user.click(freezeCheckbox)

    expect(screen.queryByLabelText('Max freeze days per year')).not.toBeInTheDocument()
  })

  it('blocks submit when required fields are empty', async () => {
    const onSubmit = vi.fn()
    const user = userEvent.setup()

    renderWithProviders(
      <PlanForm
        onSubmit={onSubmit}
        onCancel={vi.fn()}
        isSubmitting={false}
      />,
    )

    await user.click(screen.getByRole('button', { name: 'Add plan' }))

    await waitFor(() => {
      expect(onSubmit).not.toHaveBeenCalled()
    })
  })
})
