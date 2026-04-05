import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import { FreezeForm } from './FreezeForm'
import i18n from '@/i18n'

vi.mock('@/api/memberships', () => ({
  freezeMembership: vi.fn().mockResolvedValue({}),
  membershipKeys: {
    all: ['memberships'],
    active: (memberId: string) => ['memberships', 'active', memberId],
    histories: () => ['memberships', 'history'],
  },
}))

const defaultProps = {
  memberId: 'member-1',
  membershipId: 'ms-1',
  maxFreezeDays: 30,
  freezeDaysUsed: 10,
  onSuccess: vi.fn(),
  onCancel: vi.fn(),
}

describe('FreezeForm', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
    vi.clearAllMocks()
  })

  it('renders date pickers and reason field', () => {
    renderWithProviders(<FreezeForm {...defaultProps} />)

    // Check for freeze start date and end date inputs (type="date")
    const startDateInput = screen.getByDisplayValue(
      new Date().toISOString().split('T')[0],
    )
    expect(startDateInput).toBeInTheDocument()

    // Reason textarea
    const reasonField = screen.getByRole('textbox')
    expect(reasonField).toBeInTheDocument()
  })

  it('renders freeze title', () => {
    renderWithProviders(<FreezeForm {...defaultProps} />)

    const headings = screen.getAllByText('Freeze membership')
    // Title heading and submit button both show "Freeze membership"
    expect(headings.length).toBe(2)
    expect(headings[0].tagName).toBe('H3')
  })

  it('shows days remaining calculation when dates are selected', async () => {
    const user = userEvent.setup()
    renderWithProviders(<FreezeForm {...defaultProps} />)

    const today = new Date()

    const futureDate = new Date(today)
    futureDate.setDate(futureDate.getDate() + 10)
    const endDate = futureDate.toISOString().split('T')[0]

    // The end date input is the second date input
    const dateInputs = document.querySelectorAll('input[type="date"]')
    expect(dateInputs.length).toBe(2)

    await user.clear(dateInputs[1] as HTMLInputElement)
    await user.type(dateInputs[1] as HTMLInputElement, endDate)

    await waitFor(() => {
      // Should show freeze days calculation: "10 days freeze - 20 days remaining out of 30 max"
      expect(screen.getByText(/days freeze/i)).toBeInTheDocument()
    })
  })

  it('shows warning when freeze days exceed plan limit', async () => {
    const user = userEvent.setup()
    renderWithProviders(<FreezeForm {...defaultProps} />)

    const today = new Date()
    const futureDate = new Date(today)
    futureDate.setDate(futureDate.getDate() + 25)
    const endDate = futureDate.toISOString().split('T')[0]

    const dateInputs = document.querySelectorAll('input[type="date"]')
    await user.clear(dateInputs[1] as HTMLInputElement)
    await user.type(dateInputs[1] as HTMLInputElement, endDate)

    await waitFor(() => {
      // With maxFreezeDays=30, freezeDaysUsed=10, remaining=20.
      // Requesting 25 days exceeds remaining (20).
      expect(screen.getByText(/exceed/i)).toBeInTheDocument()
    })
  })

  it('disables submit when freeze days exceed limit', async () => {
    const user = userEvent.setup()
    renderWithProviders(<FreezeForm {...defaultProps} />)

    const today = new Date()
    const futureDate = new Date(today)
    futureDate.setDate(futureDate.getDate() + 25)
    const endDate = futureDate.toISOString().split('T')[0]

    const dateInputs = document.querySelectorAll('input[type="date"]')
    await user.clear(dateInputs[1] as HTMLInputElement)
    await user.type(dateInputs[1] as HTMLInputElement, endDate)

    await waitFor(() => {
      const submitButton = screen.getByRole('button', { name: /freeze membership/i })
      expect(submitButton).toBeDisabled()
    })
  })

  it('has submit disabled initially when no end date is selected', () => {
    renderWithProviders(<FreezeForm {...defaultProps} />)

    const submitButton = screen.getByRole('button', { name: /freeze membership/i })
    expect(submitButton).toBeDisabled()
  })

  it('renders cancel button that calls onCancel', async () => {
    const user = userEvent.setup()
    renderWithProviders(<FreezeForm {...defaultProps} />)

    const cancelButton = screen.getByRole('button', { name: /cancel/i })
    await user.click(cancelButton)

    expect(defaultProps.onCancel).toHaveBeenCalledOnce()
  })
})
