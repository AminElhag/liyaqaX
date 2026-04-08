import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '@/test/render'
import { ShortSurplusBadge } from './ShortSurplusBadge'

describe('ShortSurplusBadge', () => {
  it('renders nothing when difference is null', () => {
    const { container } = renderWithProviders(
      <ShortSurplusBadge difference={null} />,
    )
    expect(container.firstChild).toBeNull()
  })

  it('renders surplus badge for positive difference', () => {
    renderWithProviders(
      <ShortSurplusBadge difference={{ halalas: 1500, sar: '15.00' }} />,
    )
    const el = screen.getByText(/15\.00/)
    expect(el).toBeInTheDocument()
    expect(el.className).toContain('green')
  })

  it('renders shortage badge for negative difference', () => {
    renderWithProviders(
      <ShortSurplusBadge difference={{ halalas: -2000, sar: '-20.00' }} />,
    )
    const el = screen.getByText(/-20\.00/)
    expect(el).toBeInTheDocument()
    expect(el.className).toContain('red')
  })

  it('renders exact badge for zero difference', () => {
    const { container } = renderWithProviders(
      <ShortSurplusBadge difference={{ halalas: 0, sar: '0.00' }} />,
    )
    const badge = container.querySelector('span')
    expect(badge).toBeInTheDocument()
    expect(badge?.className).toContain('gray')
  })
})
