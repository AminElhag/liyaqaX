import { describe, it, expect, vi } from 'vitest'
import { screen, fireEvent } from '@testing-library/react'
import { renderWithProviders } from '@/test/render'
import { ReportDateRangePicker } from './ReportDateRangePicker'

describe('ReportDateRangePicker', () => {
  it('renders from and to date inputs', () => {
    const { container } = renderWithProviders(
      <ReportDateRangePicker
        from="2025-01-01"
        to="2025-12-31"
        onFromChange={() => {}}
        onToChange={() => {}}
      />,
    )
    const inputs = container.querySelectorAll('input[type="date"]')
    expect(inputs.length).toBe(2)
  })

  it('renders preset buttons', () => {
    renderWithProviders(
      <ReportDateRangePicker
        from="2025-01-01"
        to="2025-12-31"
        onFromChange={() => {}}
        onToChange={() => {}}
      />,
    )
    expect(screen.getAllByRole('button').length).toBeGreaterThanOrEqual(5)
  })

  it('calls onFromChange and onToChange when preset is clicked', () => {
    const onFrom = vi.fn()
    const onTo = vi.fn()
    renderWithProviders(
      <ReportDateRangePicker
        from="2025-01-01"
        to="2025-12-31"
        onFromChange={onFrom}
        onToChange={onTo}
      />,
    )
    const buttons = screen.getAllByRole('button')
    fireEvent.click(buttons[0])
    expect(onFrom).toHaveBeenCalled()
    expect(onTo).toHaveBeenCalled()
  })
})
