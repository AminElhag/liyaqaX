import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import type { PermissionItem } from '@/types/domain'

// Inline component for testing (avoids routing dependencies)
function PermissionCheckboxGroup({
  permissions,
  selectedIds,
  onToggle,
}: {
  permissions: PermissionItem[]
  selectedIds: Set<string>
  onToggle: (id: string) => void
}) {
  const groups: Record<string, PermissionItem[]> = {}
  for (const perm of permissions) {
    const domain = perm.code.split(':')[0]
    if (!groups[domain]) groups[domain] = []
    groups[domain].push(perm)
  }

  return (
    <div>
      {Object.entries(groups).map(([domain, perms]) => (
        <div key={domain}>
          <h4 data-testid={`group-${domain}`}>{domain}</h4>
          {perms.map((perm) => (
            <label key={perm.id}>
              <input
                type="checkbox"
                checked={selectedIds.has(perm.id)}
                onChange={() => onToggle(perm.id)}
              />
              {perm.code}
            </label>
          ))}
        </div>
      ))}
    </div>
  )
}

const mockPermissions: PermissionItem[] = [
  { id: '1', code: 'member:create', description: null },
  { id: '2', code: 'member:read', description: null },
  { id: '3', code: 'member:update', description: null },
  { id: '4', code: 'staff:create', description: null },
  { id: '5', code: 'staff:read', description: null },
]

describe('PermissionCheckboxGroup', () => {
  it('groups permissions by domain prefix', () => {
    render(
      <PermissionCheckboxGroup
        permissions={mockPermissions}
        selectedIds={new Set()}
        onToggle={() => {}}
      />,
    )

    expect(screen.getByTestId('group-member')).toBeInTheDocument()
    expect(screen.getByTestId('group-staff')).toBeInTheDocument()
  })

  it('shows checked state for selected permissions', () => {
    render(
      <PermissionCheckboxGroup
        permissions={mockPermissions}
        selectedIds={new Set(['1', '4'])}
        onToggle={() => {}}
      />,
    )

    const checkboxes = screen.getAllByRole('checkbox')
    expect(checkboxes[0]).toBeChecked() // member:create
    expect(checkboxes[1]).not.toBeChecked() // member:read
    expect(checkboxes[3]).toBeChecked() // staff:create
  })

  it('calls onToggle when a checkbox is clicked', async () => {
    const user = userEvent.setup()
    const onToggle = vi.fn()

    render(
      <PermissionCheckboxGroup
        permissions={mockPermissions}
        selectedIds={new Set()}
        onToggle={onToggle}
      />,
    )

    const checkboxes = screen.getAllByRole('checkbox')
    await user.click(checkboxes[0])

    expect(onToggle).toHaveBeenCalledWith('1')
  })
})
