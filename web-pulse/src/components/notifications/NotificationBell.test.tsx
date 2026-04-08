import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

vi.mock('@/hooks/useNotifications', () => ({
  useNotifications: () => ({
    unreadCount: 5,
    notifications: [],
    isLoadingList: false,
    fetchList: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
  }),
}))

import { NotificationBell } from './NotificationBell'

describe('NotificationBell', () => {
  it('renders bell button', () => {
    render(<NotificationBell onClick={vi.fn()} />)
    expect(screen.getByRole('button', { name: /notifications/i })).toBeInTheDocument()
  })

  it('calls onClick when clicked', async () => {
    const onClick = vi.fn()
    render(<NotificationBell onClick={onClick} />)
    await userEvent.click(screen.getByRole('button', { name: /notifications/i }))
    expect(onClick).toHaveBeenCalledOnce()
  })

  it('shows badge when count > 0', () => {
    render(<NotificationBell onClick={vi.fn()} />)
    expect(screen.getByText('5')).toBeInTheDocument()
  })
})
