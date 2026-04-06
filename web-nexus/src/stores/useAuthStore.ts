import { create } from 'zustand'

export interface AuthUser {
  id: string
  email: string
  scope: string
  roleId: string
  roleName: string
  organizationId: string | null
  clubId: string | null
  branchIds: string[]
}

interface AuthState {
  accessToken: string | null
  user: AuthUser | null
  permissions: Set<string>
  isAuthenticated: boolean
  setAuth: (token: string, user: AuthUser, permissions: string[]) => void
  clearAuth: () => void
}

export const useAuthStore = create<AuthState>()((set) => ({
  accessToken: null,
  user: null,
  permissions: new Set<string>(),
  isAuthenticated: false,

  setAuth: (token, user, permissions) =>
    set({
      accessToken: token,
      user,
      permissions: new Set(permissions),
      isAuthenticated: true,
    }),

  clearAuth: () =>
    set({
      accessToken: null,
      user: null,
      permissions: new Set<string>(),
      isAuthenticated: false,
    }),
}))
