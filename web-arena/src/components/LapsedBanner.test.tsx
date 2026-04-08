import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { LapsedBanner } from './LapsedBanner'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => {
      if (key === 'lapsed.banner_title') return 'Membership Expired'
      if (key === 'lapsed.banner_body') return 'Your membership has expired. Please visit the gym or contact staff to renew.'
      return key
    },
  }),
}))

vi.mock('@/stores/useAuthStore', () => ({
  useAuthStore: () => ({
    portalSettings: { portalMessage: null },
  }),
}))

describe('LapsedBanner', () => {
  it('renders full-screen banner with title and body', () => {
    render(<LapsedBanner />)

    expect(screen.getByText('Membership Expired')).toBeInTheDocument()
    expect(
      screen.getByText('Your membership has expired. Please visit the gym or contact staff to renew.'),
    ).toBeInTheDocument()
  })

  it('does not have a dismiss button', () => {
    render(<LapsedBanner />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})
