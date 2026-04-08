import { describe, it, expect, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithProviders } from '@/test/render'
import { PermissionGate } from './PermissionGate'
import { useAuthStore } from '@/stores/useAuthStore'
import { Permission } from '@/types/permissions'

const mockUser = {
  id: 'user-1',
  email: 'test@example.com',
  scope: 'club',
  roleId: 'role-1',
  roleName: 'Owner',
  organizationId: 'org-1',
  clubId: 'club-1',
  branchIds: [],
}

describe('PermissionGate', () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth()
  })

  it('renders children when user has the required permission', () => {
    useAuthStore.getState().setAuth('token', mockUser, [Permission.STAFF_CREATE])

    renderWithProviders(
      <PermissionGate permission={Permission.STAFF_CREATE}>
        <button>Add staff</button>
      </PermissionGate>,
    )

    expect(screen.getByRole('button', { name: 'Add staff' })).toBeInTheDocument()
  })

  it('removes children from DOM when user lacks the permission', () => {
    useAuthStore.getState().setAuth('token', mockUser, [Permission.STAFF_READ])

    renderWithProviders(
      <PermissionGate permission={Permission.STAFF_CREATE}>
        <button>Add staff</button>
      </PermissionGate>,
    )

    expect(screen.queryByRole('button', { name: 'Add staff' })).not.toBeInTheDocument()
  })

  it('renders fallback when user lacks permission and fallback is provided', () => {
    useAuthStore.getState().setAuth('token', mockUser, [])

    renderWithProviders(
      <PermissionGate
        permission={Permission.STAFF_READ}
        fallback={<p>No access</p>}
      >
        <button>View staff</button>
      </PermissionGate>,
    )

    expect(screen.queryByRole('button', { name: 'View staff' })).not.toBeInTheDocument()
    expect(screen.getByText('No access')).toBeInTheDocument()
  })

  it('removes children when user has no permissions at all', () => {
    useAuthStore.getState().setAuth('token', mockUser, [])

    renderWithProviders(
      <PermissionGate permission={Permission.STAFF_CREATE}>
        <button>Add staff</button>
      </PermissionGate>,
    )

    expect(screen.queryByRole('button', { name: 'Add staff' })).not.toBeInTheDocument()
  })
})
