import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import { FilterBuilder } from './FilterBuilder'
import type { FilterMeta } from '@/api/reportBuilder'

const sampleFilters: FilterMeta[] = [
  { code: 'branch_id', label: 'Branch', labelAr: '' },
  { code: 'plan_id', label: 'Membership Plan', labelAr: '' },
]

describe('FilterBuilder', () => {
  it('renders filter inputs', () => {
    renderWithProviders(
      <FilterBuilder availableFilters={sampleFilters} values={{}} onChange={() => {}} />,
    )
    expect(screen.getByText('Branch')).toBeInTheDocument()
    expect(screen.getByText('Membership Plan')).toBeInTheDocument()
  })

  it('calls onChange when filter value is entered', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    renderWithProviders(
      <FilterBuilder availableFilters={sampleFilters} values={{}} onChange={onChange} />,
    )
    const input = screen.getByPlaceholderText('Branch ID')
    await user.type(input, '1')
    expect(onChange).toHaveBeenCalled()
  })
})
