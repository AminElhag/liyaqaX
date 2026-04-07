import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import { MetricSelector } from './MetricSelector'
import type { MetricMeta } from '@/api/reportBuilder'

const sampleMetrics: MetricMeta[] = [
  { code: 'revenue', label: 'Revenue', labelAr: '', unit: 'sar', scope: 'revenue', description: '' },
  { code: 'new_members', label: 'New Members', labelAr: '', unit: 'count', scope: 'members', description: '' },
  { code: 'gx_bookings', label: 'GX Bookings', labelAr: '', unit: 'count', scope: 'gx', description: '' },
]

describe('MetricSelector', () => {
  it('renders grouped metrics', () => {
    renderWithProviders(
      <MetricSelector
        metrics={sampleMetrics}
        selected={[]}
        incompatibleCodes={new Set()}
        onChange={() => {}}
      />,
    )
    expect(screen.getByText('Revenue')).toBeInTheDocument()
    expect(screen.getByText('New Members')).toBeInTheDocument()
    expect(screen.getByText('GX Bookings')).toBeInTheDocument()
  })

  it('calls onChange when a metric is clicked', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    renderWithProviders(
      <MetricSelector
        metrics={sampleMetrics}
        selected={[]}
        incompatibleCodes={new Set()}
        onChange={onChange}
      />,
    )
    await user.click(screen.getByText('Revenue'))
    expect(onChange).toHaveBeenCalledWith(['revenue'])
  })

  it('shows incompatible metrics as disabled', () => {
    renderWithProviders(
      <MetricSelector
        metrics={sampleMetrics}
        selected={[]}
        incompatibleCodes={new Set(['gx_bookings'])}
        onChange={() => {}}
      />,
    )
    const btn = screen.getByText('GX Bookings').closest('button')
    expect(btn).toBeDisabled()
  })
})
