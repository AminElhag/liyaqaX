import { render, screen } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import { PermissionGate } from './PermissionGate'
import { useAuthStore } from '@/stores/useAuthStore'
import { Permission } from '@/types/permissions'

describe('PermissionGate', () => {
  beforeEach(() => {
    useAuthStore.setState({
      permissions: new Set<string>(),
      isAuthenticated: false,
      accessToken: null,
      user: null,
    })
  })

  it('renders children when user has the required permission', () => {
    useAuthStore.setState({
      permissions: new Set([Permission.ORGANIZATION_READ]),
    })

    render(
      <PermissionGate permission={Permission.ORGANIZATION_READ}>
        <span data-testid="protected">Protected content</span>
      </PermissionGate>,
    )

    expect(screen.getByTestId('protected')).toBeInTheDocument()
  })

  it('does not render children when user lacks the required permission', () => {
    useAuthStore.setState({
      permissions: new Set([Permission.MEMBER_READ]),
    })

    render(
      <PermissionGate permission={Permission.ORGANIZATION_READ}>
        <span data-testid="protected">Protected content</span>
      </PermissionGate>,
    )

    expect(screen.queryByTestId('protected')).not.toBeInTheDocument()
  })

  it('does not render children when user has no permissions', () => {
    render(
      <PermissionGate permission={Permission.AUDIT_READ}>
        <span data-testid="protected">Protected content</span>
      </PermissionGate>,
    )

    expect(screen.queryByTestId('protected')).not.toBeInTheDocument()
  })
})
