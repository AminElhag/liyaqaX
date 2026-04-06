import { describe, it, expect } from 'vitest'
import { renderWithProviders } from '@/test/render'
import { EmptyReportState } from './EmptyReportState'

describe('EmptyReportState', () => {
  it('renders empty state container', () => {
    const { container } = renderWithProviders(<EmptyReportState />)
    const emptyDiv = container.querySelector('.border-dashed')
    expect(emptyDiv).toBeInTheDocument()
    expect(emptyDiv?.querySelector('p')).toBeInTheDocument()
  })
})
