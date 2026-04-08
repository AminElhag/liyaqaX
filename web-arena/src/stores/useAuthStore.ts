import { create } from 'zustand'
import type { MemberMe, PortalSettings } from '@/types/domain'

interface AuthState {
  accessToken: string | null
  member: MemberMe | null
  portalSettings: PortalSettings | null
  isAuthenticated: boolean
  setAuth: (token: string) => void
  setMember: (member: MemberMe) => void
  setPortalSettings: (settings: PortalSettings) => void
  clearAuth: () => void
}

export const useAuthStore = create<AuthState>()((set) => ({
  accessToken: null,
  member: null,
  portalSettings: null,
  isAuthenticated: false,

  setAuth: (token) =>
    set({ accessToken: token, isAuthenticated: true }),

  setMember: (member) =>
    set({ member }),

  setPortalSettings: (settings) =>
    set({ portalSettings: settings }),

  clearAuth: () =>
    set({
      accessToken: null,
      member: null,
      portalSettings: null,
      isAuthenticated: false,
    }),
}))
