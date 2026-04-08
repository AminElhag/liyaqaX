import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import { DimensionSelector } from './DimensionSelector'
import type { DimensionMeta } from '@/api/reportBuilder'

const sampleDimensions: DimensionMeta[] = [
  { code: 'month', label: 'Month', labelAr: '', compatibleMetricScopes: ['revenue', 'members'] },
  { code: 'branch', label: 'Branch', labelAr: '', compatibleMetricScopes: ['revenue', 'members'] },
  { code: 'lead_source', label: 'Lead Source', labelAr: '', compatibleMetricScopes: ['leads'] },
]

describe('DimensionSelector', () => {
  it('renders all dimensions', () => {
    renderWithProviders(
      <DimensionSelector dimensions={sampleDimensions} selected={[]} onChange={() => {}} />,
    )
    expect(screen.getByText('Month')).toBeInTheDocument()
    expect(screen.getByText('Branch')).toBeInTheDocument()
    expect(screen.getByText('Lead Source')).toBeInTheDocument()
  })

  it('selects a primary dimension', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    renderWithProviders(
      <DimensionSelector dimensions={sampleDimensions} selected={[]} onChange={onChange} />,
    )
    await user.click(screen.getByText('Month'))
    expect(onChange).toHaveBeenCalledWith(['month'])
  })

  it('shows second dimension options when primary is selected', () => {
    renderWithProviders(
      <DimensionSelector dimensions={sampleDimensions} selected={['month']} onChange={() => {}} />,
    )
    expect(screen.getByText('Second dimension (optional)')).toBeInTheDocument()
  })
})
