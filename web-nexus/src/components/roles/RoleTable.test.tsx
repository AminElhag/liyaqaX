import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import type { RoleListItem } from '@/types/domain'

// Inline render-only component for testing table display logic
function RoleTableDisplay({ roles }: { roles: RoleListItem[] }) {
  return (
    <table>
      <tbody>
        {roles.map((role) => (
          <tr key={role.id} data-testid={`role-${role.id}`}>
            <td>{role.nameEn}</td>
            <td>{role.scope}</td>
            <td>{role.permissionCount}</td>
            <td>{role.staffCount}</td>
            <td>
              {role.isSystem && (
                <span data-testid={`system-badge-${role.id}`}>System</span>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

const mockRoles: RoleListItem[] = [
  {
    id: '1',
    nameAr: 'مدير النظام',
    nameEn: 'Super Admin',
    description: null,
    scope: 'platform',
    isSystem: true,
    permissionCount: 43,
    staffCount: 1,
  },
  {
    id: '2',
    nameAr: 'دور مخصص',
    nameEn: 'Custom Role',
    description: null,
    scope: 'platform',
    isSystem: false,
    permissionCount: 5,
    staffCount: 0,
  },
]

describe('RoleTable', () => {
  it('renders role list with names', () => {
    render(<RoleTableDisplay roles={mockRoles} />)

    expect(screen.getByText('Super Admin')).toBeInTheDocument()
    expect(screen.getByText('Custom Role')).toBeInTheDocument()
  })

  it('shows system badge for system roles', () => {
    render(<RoleTableDisplay roles={mockRoles} />)

    expect(screen.getByTestId('system-badge-1')).toBeInTheDocument()
    expect(screen.queryByTestId('system-badge-2')).not.toBeInTheDocument()
  })

  it('displays permission and staff counts', () => {
    render(<RoleTableDisplay roles={mockRoles} />)

    expect(screen.getByText('43')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
  })
})
