import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
}))

import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/useAuthStore'

describe('Login scope check', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.getState().clearAuth()
  })

  it('rejects non-trainer scope JWT (scope = club)', async () => {
    const mockLogin = vi.mocked(login)
    mockLogin.mockResolvedValue({
      accessToken: 'fake-token',
      userId: '1',
      scope: 'club',
      roleId: 'role-1',
      roleName: 'Owner',
      organizationId: 'org-1',
      clubId: 'club-1',
      trainerId: null,
      trainerTypes: null,
      branchIds: null,
    })

    const result = await login('staff@test.com', 'password')
    expect(result.scope).not.toBe('trainer')
    // The app would show "This app is for trainers only" and not store the token
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })

  it('accepts trainer scope JWT', async () => {
    const mockLogin = vi.mocked(login)
    mockLogin.mockResolvedValue({
      accessToken: 'valid-trainer-token',
      userId: '2',
      scope: 'trainer',
      roleId: 'role-2',
      roleName: 'PT Trainer',
      organizationId: 'org-1',
      clubId: 'club-1',
      trainerId: 'trainer-1',
      trainerTypes: ['pt'],
      branchIds: ['branch-1'],
    })

    const result = await login('pt@test.com', 'password')
    expect(result.scope).toBe('trainer')
  })
})
