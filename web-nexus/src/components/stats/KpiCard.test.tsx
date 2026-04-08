import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { KpiCard } from './KpiCard'

describe('KpiCard', () => {
  it('renders label and value', () => {
    render(<KpiCard label="Total Organizations" value={42} />)

    expect(screen.getByText('Total Organizations')).toBeInTheDocument()
    expect(screen.getByText('42')).toBeInTheDocument()
  })

  it('renders string value', () => {
    render(<KpiCard label="Revenue" value="SAR 1,500.00" />)

    expect(screen.getByText('Revenue')).toBeInTheDocument()
    expect(screen.getByText('SAR 1,500.00')).toBeInTheDocument()
  })

  it('renders tooltip button when tooltip is provided', () => {
    render(
      <KpiCard
        label="MRR"
        value="SAR 500.00"
        tooltip="Estimated monthly recurring revenue"
      />,
    )

    expect(screen.getByLabelText('Info')).toBeInTheDocument()
  })

  it('does not render tooltip button when no tooltip is provided', () => {
    render(<KpiCard label="Members" value={100} />)

    expect(screen.queryByLabelText('Info')).not.toBeInTheDocument()
  })
})
