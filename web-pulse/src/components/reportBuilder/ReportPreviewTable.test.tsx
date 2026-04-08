import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '@/test/render'
import { ReportPreviewTable } from './ReportPreviewTable'
import type { ReportResultResponse } from '@/api/reportBuilder'

const sampleResult: ReportResultResponse = {
  templateId: 'test-id',
  runAt: '2026-03-15T10:00:00Z',
  dateFrom: '2026-01-01',
  dateTo: '2026-03-31',
  columns: ['month', 'revenue'],
  rows: [
    { month: '2026-01', revenue: 150000 },
    { month: '2026-02', revenue: 200000 },
  ],
  rowCount: 2,
  truncated: false,
  fromCache: false,
}

describe('ReportPreviewTable', () => {
  it('renders columns and rows', () => {
    renderWithProviders(<ReportPreviewTable result={sampleResult} />)
    expect(screen.getByText('month')).toBeInTheDocument()
    expect(screen.getByText('revenue')).toBeInTheDocument()
    expect(screen.getByText('2026-01')).toBeInTheDocument()
    expect(screen.getByText('150000')).toBeInTheDocument()
    expect(screen.getByText('2 rows')).toBeInTheDocument()
  })

  it('shows extra badge when fromCache is true', () => {
    const { container } = renderWithProviders(
      <ReportPreviewTable result={{ ...sampleResult, fromCache: true }} />,
    )
    const badges = container.querySelectorAll('.bg-blue-50')
    expect(badges.length).toBeGreaterThan(0)
  })

  it('shows truncated row count when truncated', () => {
    renderWithProviders(
      <ReportPreviewTable result={{ ...sampleResult, truncated: true, rowCount: 50000 }} />,
    )
    expect(screen.getByText('50000 rows')).toBeInTheDocument()
  })
})
