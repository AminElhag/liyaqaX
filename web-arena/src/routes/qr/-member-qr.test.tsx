import { describe, it, expect, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '@/test/render'
import i18n from '@/i18n'

describe('MemberQrPage', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
  })

  it('renders QR code with member publicId', () => {
    const testUuid = '3fa85f64-5717-4562-b3fc-2c963f66afa6'

    renderWithProviders(
      <div>
        <svg data-testid="qr-code" />
        <p>{testUuid}</p>
      </div>,
    )

    expect(screen.getByTestId('qr-code')).toBeInTheDocument()
    expect(screen.getByText(testUuid)).toBeInTheDocument()
  })

  it('renders member name below QR', () => {
    renderWithProviders(
      <div>
        <svg data-testid="qr-code" />
        <p data-testid="member-name">Ahmed Al-Rashidi</p>
        <p>Show this to the receptionist to check in</p>
      </div>,
    )

    expect(screen.getByTestId('member-name')).toHaveTextContent('Ahmed Al-Rashidi')
    expect(screen.getByText('Show this to the receptionist to check in')).toBeInTheDocument()
  })
})
