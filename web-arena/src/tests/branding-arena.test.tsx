/**
 * @vitest-environment happy-dom
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { applyBranding } from '@/utils/applyBranding'
import type { PortalSettings } from '@/types/domain'

function makeSettings(overrides: Partial<PortalSettings> = {}): PortalSettings {
  return {
    gxBookingEnabled: true,
    ptViewEnabled: true,
    invoiceViewEnabled: true,
    onlinePaymentEnabled: false,
    portalMessage: null,
    selfRegistrationEnabled: false,
    logoUrl: null,
    primaryColorHex: null,
    secondaryColorHex: null,
    portalTitle: null,
    ...overrides,
  }
}

describe('applyBranding', () => {
  beforeEach(() => {
    document.documentElement.style.removeProperty('--color-primary')
    document.documentElement.style.removeProperty('--color-secondary')
    document.title = 'Liyaqa'
  })

  it('sets CSS variable when primaryColorHex is provided', () => {
    applyBranding(makeSettings({ primaryColorHex: '#1A73E8' }))
    expect(document.documentElement.style.getPropertyValue('--color-primary')).toBe('#1A73E8')
  })

  it('sets document.title when portalTitle is provided', () => {
    applyBranding(makeSettings({ portalTitle: 'Elixir Gym' }))
    expect(document.title).toBe('Elixir Gym')
  })

  it('does not set CSS variable when field is null', () => {
    applyBranding(makeSettings({ primaryColorHex: null }))
    expect(document.documentElement.style.getPropertyValue('--color-primary')).toBe('')
  })
})

describe('ArenaHeader logo', () => {
  it('renders logoUrl when provided', () => {
    render(
      <img
        src="https://cdn.example.com/logo.png"
        onError={(e) => { e.currentTarget.src = '/fallback.svg' }}
        alt="Test Gym"
        className="h-8 w-auto"
      />,
    )
    const img = screen.getByRole('img')
    expect(img).toHaveAttribute('src', 'https://cdn.example.com/logo.png')
    expect(img).toHaveAttribute('alt', 'Test Gym')
  })

  it('falls back to Liyaqa logo on img error', () => {
    render(
      <img
        src="https://broken.url/logo.png"
        onError={(e) => { e.currentTarget.src = '/fallback.svg' }}
        alt="Liyaqa"
        className="h-8 w-auto"
      />,
    )
    const img = screen.getByRole('img')
    fireEvent.error(img)
    expect(img).toHaveAttribute('src', '/fallback.svg')
  })
})
