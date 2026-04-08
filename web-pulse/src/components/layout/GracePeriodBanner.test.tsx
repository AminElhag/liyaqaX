import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { GracePeriodBanner } from './GracePeriodBanner'
import { useGraceStore } from '@/stores/useGraceStore'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      if (key === 'subscription.grace_banner' && params) {
        return `Your club subscription has expired. Access will be removed in ${params.days} days. Contact support to renew.`
      }
      return key
    },
    i18n: { language: 'en' },
  }),
}))

describe('GracePeriodBanner', () => {
  beforeEach(() => {
    useGraceStore.setState({ isGrace: false, daysRemaining: 0 })
  })

  it('renders banner when X-Subscription-Grace header is present', () => {
    useGraceStore.setState({ isGrace: true, daysRemaining: 5 })

    render(<GracePeriodBanner />)

    expect(
      screen.getByText(/Your club subscription has expired/),
    ).toBeInTheDocument()
  })

  it('shows correct days remaining', () => {
    useGraceStore.setState({ isGrace: true, daysRemaining: 3 })

    render(<GracePeriodBanner />)

    expect(screen.getByText(/3 days/)).toBeInTheDocument()
  })

  it('does not render when header is absent', () => {
    useGraceStore.setState({ isGrace: false, daysRemaining: 0 })

    const { container } = render(<GracePeriodBanner />)

    expect(container.innerHTML).toBe('')
  })
})
