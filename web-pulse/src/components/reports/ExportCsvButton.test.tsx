import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '@/test/render'
import { ExportCsvButton } from './ExportCsvButton'

describe('ExportCsvButton', () => {
  it('renders export button', () => {
    renderWithProviders(<ExportCsvButton href="/api/v1/reports/revenue/export?from=2025-01-01&to=2025-12-31" />)
    expect(screen.getByRole('button')).toBeInTheDocument()
  })
})
