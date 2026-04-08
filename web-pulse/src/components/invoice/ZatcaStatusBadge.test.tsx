import { describe, it, expect, beforeEach } from 'vitest'
import { renderWithProviders } from '@/test/render'
import { ZatcaStatusBadge } from './ZatcaStatusBadge'
import i18n from '@/i18n'

describe('ZatcaStatusBadge', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
  })

  it('renders pending with amber styling', () => {
    const { container } = renderWithProviders(
      <ZatcaStatusBadge status="pending" />,
    )
    expect(container.querySelector('span')).toHaveClass('bg-amber-100')
    expect(container.querySelector('span')?.textContent).toBe('Pending')
  })

  it('renders generated with green styling', () => {
    const { container } = renderWithProviders(
      <ZatcaStatusBadge status="generated" />,
    )
    expect(container.querySelector('span')).toHaveClass('bg-green-100')
    expect(container.querySelector('span')?.textContent).toBe('Generated')
  })

  it('renders submitted with blue styling', () => {
    const { container } = renderWithProviders(
      <ZatcaStatusBadge status="submitted" />,
    )
    expect(container.querySelector('span')).toHaveClass('bg-blue-100')
    expect(container.querySelector('span')?.textContent).toBe('Submitted')
  })

  it('renders accepted with green styling', () => {
    const { container } = renderWithProviders(
      <ZatcaStatusBadge status="accepted" />,
    )
    expect(container.querySelector('span')).toHaveClass('bg-green-100')
    expect(container.querySelector('span')?.textContent).toBe('Accepted')
  })

  it('renders rejected with red styling', () => {
    const { container } = renderWithProviders(
      <ZatcaStatusBadge status="rejected" />,
    )
    expect(container.querySelector('span')).toHaveClass('bg-red-100')
    expect(container.querySelector('span')?.textContent).toBe('Rejected')
  })
})
