import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { NotificationItem } from './NotificationItem'
import type { NotificationResponse } from '@/api/notifications'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, unknown>) => {
      if (key === 'notification.payment_collected.title') return 'Payment received'
      if (key === 'notification.payment_collected.body')
        return `A payment of ${params?.amountSar} SAR has been collected.`
      return key
    },
  }),
}))

const baseNotification: NotificationResponse = {
  id: 'n-1',
  type: 'PAYMENT_COLLECTED',
  titleKey: 'notification.payment_collected.title',
  bodyKey: 'notification.payment_collected.body',
  params: { amountSar: '150.00' },
  entityType: 'Payment',
  entityId: 'p-1',
  readAt: null,
  createdAt: new Date().toISOString(),
}

describe('NotificationItem', () => {
  it('renders i18n resolved title and body with params', () => {
    render(<NotificationItem notification={baseNotification} onRead={vi.fn()} />)
    expect(screen.getByText('Payment received')).toBeInTheDocument()
    expect(screen.getByText(/150\.00 SAR/)).toBeInTheDocument()
  })

  it('shows unread dot when readAt is null', () => {
    const { container } = render(
      <NotificationItem notification={baseNotification} onRead={vi.fn()} />,
    )
    expect(container.querySelector('.bg-blue-500')).toBeInTheDocument()
  })

  it('does not show unread dot when readAt is set', () => {
    const readNotification = { ...baseNotification, readAt: new Date().toISOString() }
    const { container } = render(
      <NotificationItem notification={readNotification} onRead={vi.fn()} />,
    )
    expect(container.querySelector('.bg-blue-500')).not.toBeInTheDocument()
  })

  it('calls onRead when unread item is clicked', async () => {
    const onRead = vi.fn()
    render(<NotificationItem notification={baseNotification} onRead={onRead} />)
    await userEvent.click(screen.getByRole('button'))
    expect(onRead).toHaveBeenCalledWith('n-1')
  })

  it('does not call onRead when already read item is clicked', async () => {
    const onRead = vi.fn()
    const readNotification = { ...baseNotification, readAt: new Date().toISOString() }
    render(<NotificationItem notification={readNotification} onRead={onRead} />)
    await userEvent.click(screen.getByRole('button'))
    expect(onRead).not.toHaveBeenCalled()
  })
})
