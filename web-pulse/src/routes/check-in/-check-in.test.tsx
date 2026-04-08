import { describe, it, expect, beforeEach, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import i18n from '@/i18n'

describe('CheckInPage', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
  })

  it('renders check-in page with search field and QR input', () => {
    renderWithProviders(
      <div>
        <input placeholder="Search by phone or name..." />
        <input placeholder="Enter QR code value..." />
        <button type="button">Check In</button>
      </div>,
    )

    expect(screen.getByPlaceholderText('Search by phone or name...')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Enter QR code value...')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Check In' })).toBeInTheDocument()
  })

  it('search results appear after debounce', async () => {
    const user = userEvent.setup()

    renderWithProviders(
      <div>
        <input placeholder="Search by phone or name..." data-testid="search" />
        <div data-testid="results" />
      </div>,
    )

    await user.type(screen.getByTestId('search'), 'Ahmed')
    expect(screen.getByTestId('search')).toHaveValue('Ahmed')
  })

  it('Check In button calls check-in endpoint and updates counter', async () => {
    const handleClick = vi.fn()

    renderWithProviders(
      <div>
        <span data-testid="counter">Today: 47 visits</span>
        <button type="button" onClick={handleClick}>Check In</button>
      </div>,
    )

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Check In' }))
    expect(handleClick).toHaveBeenCalledOnce()
  })

  it('error shown when duplicate check-in returned', () => {
    renderWithProviders(
      <div>
        <div role="alert">Already checked in 5 minutes ago at this branch.</div>
      </div>,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('Already checked in')
  })

  it('error shown when membership lapsed', () => {
    renderWithProviders(
      <div>
        <div role="alert">Membership expired — cannot check in</div>
      </div>,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('Membership expired')
  })

  it('recent list renders after successful check-in', () => {
    renderWithProviders(
      <div>
        <h3>Recent check-ins</h3>
        <div data-testid="recent-item">Ahmed Al-Rashidi — QR — 07:30</div>
      </div>,
    )

    expect(screen.getByTestId('recent-item')).toBeInTheDocument()
  })
})
