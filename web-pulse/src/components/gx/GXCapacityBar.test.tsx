import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '@/test/render'
import { GXCapacityBar } from './GXCapacityBar'

describe('GXCapacityBar', () => {
  it('shows correct count text', () => {
    renderWithProviders(<GXCapacityBar bookingsCount={5} capacity={15} />)
    expect(screen.getByText('5/15')).toBeInTheDocument()
  })

  it('shows green bar when under 80% capacity', () => {
    const { container } = renderWithProviders(
      <GXCapacityBar bookingsCount={5} capacity={15} />,
    )
    const bar = container.querySelector('[style]')
    expect(bar).toHaveClass('bg-green-500')
  })

  it('shows amber bar when at 80% or above capacity', () => {
    const { container } = renderWithProviders(
      <GXCapacityBar bookingsCount={12} capacity={15} />,
    )
    const bar = container.querySelector('[style]')
    expect(bar).toHaveClass('bg-amber-500')
  })

  it('shows red bar when fully booked', () => {
    const { container } = renderWithProviders(
      <GXCapacityBar bookingsCount={15} capacity={15} />,
    )
    const bar = container.querySelector('[style]')
    expect(bar).toHaveClass('bg-red-500')
  })

  it('handles zero capacity without errors', () => {
    renderWithProviders(<GXCapacityBar bookingsCount={0} capacity={0} />)
    expect(screen.getByText('0/0')).toBeInTheDocument()
  })
})
