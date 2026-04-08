import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { WaitlistBanner } from './WaitlistBanner'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, string>) => {
      if (key === 'gx.waitlist.offered_banner') return `A spot is available! Accept ${params?.deadline}`
      if (key === 'gx.waitlist.accept') return 'Accept Spot'
      if (key === 'common.loading') return 'Loading...'
      return key
    },
  }),
}))

describe('WaitlistBanner', () => {
  it('shows accept button when offer is not expired', () => {
    const futureDate = new Date(Date.now() + 60 * 60 * 1000).toISOString()
    render(
      <WaitlistBanner
        offerExpiresAt={futureDate}
        onAccept={() => {}}
        isAccepting={false}
      />,
    )

    expect(screen.getByText('Accept Spot')).toBeInTheDocument()
  })

  it('calls onAccept when accept button is clicked', () => {
    const onAccept = vi.fn()
    const futureDate = new Date(Date.now() + 60 * 60 * 1000).toISOString()
    render(
      <WaitlistBanner
        offerExpiresAt={futureDate}
        onAccept={onAccept}
        isAccepting={false}
      />,
    )

    fireEvent.click(screen.getByText('Accept Spot'))
    expect(onAccept).toHaveBeenCalledOnce()
  })

  it('disables accept button when isAccepting is true', () => {
    const futureDate = new Date(Date.now() + 60 * 60 * 1000).toISOString()
    render(
      <WaitlistBanner
        offerExpiresAt={futureDate}
        onAccept={() => {}}
        isAccepting={true}
      />,
    )

    const button = screen.getByText('Loading...')
    expect(button).toBeDisabled()
  })

  it('renders nothing when offer is expired', () => {
    const pastDate = new Date(Date.now() - 60 * 60 * 1000).toISOString()
    const { container } = render(
      <WaitlistBanner
        offerExpiresAt={pastDate}
        onAccept={() => {}}
        isAccepting={false}
      />,
    )

    expect(container.firstChild).toBeNull()
  })
})
