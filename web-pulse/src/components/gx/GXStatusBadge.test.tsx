import { describe, it, expect } from 'vitest'
import { renderWithProviders } from '@/test/render'
import { GXStatusBadge } from './GXStatusBadge'
import i18n from '@/i18n'

describe('GXStatusBadge', () => {
  beforeEach(() => {
    i18n.changeLanguage('en')
  })

  it('renders instance statuses with correct styling', () => {
    const { container: c1 } = renderWithProviders(
      <GXStatusBadge status="scheduled" />,
    )
    expect(c1.querySelector('span')).toHaveClass('bg-blue-100')

    const { container: c2 } = renderWithProviders(
      <GXStatusBadge status="completed" />,
    )
    expect(c2.querySelector('span')).toHaveClass('bg-gray-100')

    const { container: c3 } = renderWithProviders(
      <GXStatusBadge status="cancelled" />,
    )
    expect(c3.querySelector('span')).toHaveClass('bg-red-100')
  })

  it('renders booking statuses with correct styling', () => {
    const { container: c1 } = renderWithProviders(
      <GXStatusBadge status="confirmed" variant="booking" />,
    )
    expect(c1.querySelector('span')).toHaveClass('bg-green-100')

    const { container: c2 } = renderWithProviders(
      <GXStatusBadge status="waitlist" variant="booking" />,
    )
    expect(c2.querySelector('span')).toHaveClass('bg-amber-100')

    const { container: c3 } = renderWithProviders(
      <GXStatusBadge status="cancelled" variant="booking" />,
    )
    expect(c3.querySelector('span')).toHaveClass('bg-red-100')

    const { container: c4 } = renderWithProviders(
      <GXStatusBadge status="promoted" variant="booking" />,
    )
    expect(c4.querySelector('span')).toHaveClass('bg-green-100')
  })
})
