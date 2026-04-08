import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithProviders } from '@/test/render'
import { ReconcileModal } from './ReconcileModal'

describe('ReconcileModal', () => {
  it('renders nothing when not open', () => {
    const { container } = renderWithProviders(
      <ReconcileModal
        isOpen={false}
        onClose={vi.fn()}
        onConfirm={vi.fn()}
      />,
    )
    expect(container.firstChild).toBeNull()
  })

  it('shows validation error when flagging without notes', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()

    renderWithProviders(
      <ReconcileModal
        isOpen={true}
        onClose={vi.fn()}
        onConfirm={onConfirm}
      />,
    )

    // Select "flagged"
    const flagRadio = screen.getByDisplayValue('flagged')
    await user.click(flagRadio)

    // Try to confirm — click the last button (confirm button)
    const buttons = screen.getAllByRole('button')
    const confirmBtn = buttons[buttons.length - 1]
    await user.click(confirmBtn)

    // Should show error, not call onConfirm
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('calls onConfirm with approved status', async () => {
    const user = userEvent.setup()
    const onConfirm = vi.fn()

    renderWithProviders(
      <ReconcileModal
        isOpen={true}
        onClose={vi.fn()}
        onConfirm={onConfirm}
      />,
    )

    // Approved is selected by default, click confirm
    const buttons = screen.getAllByRole('button')
    const confirmBtn = buttons[buttons.length - 1]
    await user.click(confirmBtn)

    expect(onConfirm).toHaveBeenCalledWith('approved', undefined)
  })
})
