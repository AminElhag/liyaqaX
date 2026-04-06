import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import type { RoleListItem } from '@/types/domain'

// Inline component for testing role assignment logic
function StaffRoleDropdown({
  roles,
  onAssign,
  onCancel,
}: {
  roles: RoleListItem[]
  onAssign: (roleId: string) => void
  onCancel: () => void
}) {
  return (
    <div>
      <select
        data-testid="role-select"
        onChange={(e) => {
          if (e.target.value) onAssign(e.target.value)
        }}
      >
        <option value="">Select a role...</option>
        {roles.map((r) => (
          <option key={r.id} value={r.id}>
            {r.nameEn}
          </option>
        ))}
      </select>
      <button type="button" onClick={onCancel}>
        Cancel
      </button>
    </div>
  )
}

const mockClubRoles: RoleListItem[] = [
  {
    id: 'r1',
    nameAr: 'مالك',
    nameEn: 'Owner',
    description: null,
    scope: 'club',
    isSystem: true,
    permissionCount: 30,
    staffCount: 1,
  },
  {
    id: 'r2',
    nameAr: 'موظف مبيعات',
    nameEn: 'Sales Agent',
    description: null,
    scope: 'club',
    isSystem: true,
    permissionCount: 10,
    staffCount: 2,
  },
]

describe('StaffRoleAssignment', () => {
  it('shows club roles in dropdown', () => {
    render(
      <StaffRoleDropdown
        roles={mockClubRoles}
        onAssign={() => {}}
        onCancel={() => {}}
      />,
    )

    const select = screen.getByTestId('role-select')
    expect(select).toBeInTheDocument()
    expect(screen.getByText('Owner')).toBeInTheDocument()
    expect(screen.getByText('Sales Agent')).toBeInTheDocument()
  })

  it('calls onAssign when a role is selected', async () => {
    const user = userEvent.setup()
    const onAssign = vi.fn()

    render(
      <StaffRoleDropdown
        roles={mockClubRoles}
        onAssign={onAssign}
        onCancel={() => {}}
      />,
    )

    await user.selectOptions(screen.getByTestId('role-select'), 'r2')
    expect(onAssign).toHaveBeenCalledWith('r2')
  })

  it('calls onCancel when cancel is clicked', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()

    render(
      <StaffRoleDropdown
        roles={mockClubRoles}
        onAssign={() => {}}
        onCancel={onCancel}
      />,
    )

    await user.click(screen.getByText('Cancel'))
    expect(onCancel).toHaveBeenCalled()
  })
})
