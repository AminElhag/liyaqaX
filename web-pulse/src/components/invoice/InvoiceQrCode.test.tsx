import { describe, it, expect, beforeEach } from 'vitest'
import { renderWithProviders } from '@/test/render'
import { InvoiceQrCode } from './InvoiceQrCode'
import i18n from '@/i18n'

describe('InvoiceQrCode', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
  })

  it('renders QR code SVG when qrCode is provided', () => {
    const { container } = renderWithProviders(
      <InvoiceQrCode qrCode="AQR0ZXN0" />,
    )
    const svg = container.querySelector('svg')
    expect(svg).toBeTruthy()
  })

  it('renders fallback message when qrCode is null', () => {
    const { container } = renderWithProviders(
      <InvoiceQrCode qrCode={null} />,
    )
    const svg = container.querySelector('svg')
    expect(svg).toBeNull()
    expect(container.textContent).toContain('QR code not yet generated')
  })

  it('applies custom size to QR code', () => {
    const { container } = renderWithProviders(
      <InvoiceQrCode qrCode="AQR0ZXN0" size={100} />,
    )
    const svg = container.querySelector('svg')
    expect(svg?.getAttribute('height')).toBe('100')
    expect(svg?.getAttribute('width')).toBe('100')
  })
})
