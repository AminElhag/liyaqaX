import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '@/test/render'
import { ScheduleBadge } from './ScheduleBadge'
import type { ReportScheduleResponse } from '@/api/reportSchedules'
import i18n from '@/i18n'

function makeSchedule(overrides: Partial<ReportScheduleResponse> = {}): ReportScheduleResponse {
  return {
    id: '1',
    templateId: '1',
    templateName: 'Test',
    frequency: 'daily',
    recipients: ['a@test.com'],
    isActive: true,
    lastRunAt: null,
    lastRunStatus: null,
    lastError: null,
    nextRunAt: '2026-04-08T04:00:00Z',
    createdAt: '2026-04-07T04:00:00Z',
    ...overrides,
  }
}

const activeText = i18n.t('reports.schedule.active')
const pausedText = i18n.t('reports.schedule.paused')
const neverRunText = i18n.t('reports.schedule.neverRun')
const failedText = i18n.t('reports.schedule.failed')

describe('ScheduleBadge', () => {
  it('renders nothing when no schedule', () => {
    const { container } = renderWithProviders(<ScheduleBadge schedule={null} />)
    expect(container.firstChild).toBeNull()
  })

  it('renders Active badge for active schedule with successful last run', () => {
    renderWithProviders(
      <ScheduleBadge schedule={makeSchedule({ lastRunAt: '2026-04-07T04:00:00Z', lastRunStatus: 'success' })} />,
    )
    expect(screen.getByText(activeText)).toBeInTheDocument()
  })

  it('renders Paused badge when inactive', () => {
    renderWithProviders(
      <ScheduleBadge schedule={makeSchedule({ isActive: false })} />,
    )
    expect(screen.getByText(pausedText)).toBeInTheDocument()
  })

  it('renders Never run badge when active but never ran', () => {
    renderWithProviders(
      <ScheduleBadge schedule={makeSchedule()} />,
    )
    expect(screen.getByText(neverRunText)).toBeInTheDocument()
  })

  it('renders Failed badge on failed last run', () => {
    renderWithProviders(
      <ScheduleBadge schedule={makeSchedule({ lastRunAt: '2026-04-07T04:00:00Z', lastRunStatus: 'failed' })} />,
    )
    expect(screen.getByText(failedText)).toBeInTheDocument()
  })
})
