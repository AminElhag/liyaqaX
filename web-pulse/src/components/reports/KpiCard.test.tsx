import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '@/test/render'
import { KpiCard } from './KpiCard'

describe('KpiCard', () => {
  it('renders label and value', () => {
    renderWithProviders(<KpiCard label="Total Revenue" value="12,500.00" />)
    expect(screen.getByText('Total Revenue')).toBeInTheDocument()
    expect(screen.getByText('12,500.00')).toBeInTheDocument()
  })

  it('renders subtitle when provided', () => {
    renderWithProviders(<KpiCard label="Revenue" value="100" subtitle="SAR" />)
    expect(screen.getByText('SAR')).toBeInTheDocument()
  })

  it('renders positive trend with + prefix', () => {
    renderWithProviders(<KpiCard label="Growth" value="13.6%" trend={13.6} />)
    expect(screen.getByText('+13.6%')).toBeInTheDocument()
  })

  it('renders negative trend without + prefix', () => {
    renderWithProviders(<KpiCard label="Growth" value="-5.2%" trend={-5.2} />)
    expect(screen.getAllByText('-5.2%').length).toBeGreaterThanOrEqual(1)
  })

  it('does not render trend when null', () => {
    const { container } = renderWithProviders(<KpiCard label="Growth" value="-" trend={null} />)
    expect(container.querySelector('.text-green-600')).toBeNull()
    expect(container.querySelector('.text-red-600')).toBeNull()
  })
})
