import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import { ScheduleForm } from './ScheduleForm'
import i18n from '@/i18n'

const dailyText = i18n.t('reports.schedule.daily')
const weeklyText = i18n.t('reports.schedule.weekly')
const monthlyText = i18n.t('reports.schedule.monthly')
const addText = i18n.t('reports.schedule.addRecipient')
const invalidEmailText = i18n.t('reports.schedule.invalidEmail')
const nextRunText = i18n.t('reports.schedule.nextRun')

describe('ScheduleForm', () => {
  it('renders frequency options', () => {
    renderWithProviders(
      <ScheduleForm onSubmit={() => {}} isPending={false} submitLabel="Save" />,
    )
    expect(screen.getByText(dailyText)).toBeInTheDocument()
    expect(screen.getByText(weeklyText)).toBeInTheDocument()
    expect(screen.getByText(monthlyText)).toBeInTheDocument()
  })

  it('enforces max 10 recipients', () => {
    renderWithProviders(
      <ScheduleForm
        initialRecipients={Array.from({ length: 10 }, (_, i) => `user${i}@test.com`)}
        onSubmit={() => {}}
        isPending={false}
        submitLabel="Save"
      />,
    )

    const input = screen.getByPlaceholderText('email@example.com')
    expect(input).toBeDisabled()
  })

  it('shows error for invalid email', async () => {
    const user = userEvent.setup()

    renderWithProviders(
      <ScheduleForm onSubmit={() => {}} isPending={false} submitLabel="Save" />,
    )

    const input = screen.getByPlaceholderText('email@example.com')
    await user.type(input, 'not-an-email')
    await user.click(screen.getByText(addText))

    expect(screen.getByText(invalidEmailText)).toBeInTheDocument()
  })

  it('next run preview is visible with daily frequency', () => {
    renderWithProviders(
      <ScheduleForm onSubmit={() => {}} isPending={false} submitLabel="Save" />,
    )

    const nextRunEl = screen.getByText(new RegExp(nextRunText))
    expect(nextRunEl).toBeInTheDocument()
  })
})
