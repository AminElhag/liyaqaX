import { create } from 'zustand'
import type { TrainerProfile } from '@/types/domain'

interface AuthState {
  accessToken: string | null
  trainer: TrainerProfile | null
  trainerTypes: string[]
  isAuthenticated: boolean
  setAuth: (token: string, trainer: TrainerProfile) => void
  clearAuth: () => void
}

export const useAuthStore = create<AuthState>()((set) => ({
  accessToken: null,
  trainer: null,
  trainerTypes: [],
  isAuthenticated: false,

  setAuth: (token, trainer) =>
    set({
      accessToken: token,
      trainer,
      trainerTypes: trainer.trainerTypes,
      isAuthenticated: true,
    }),

  clearAuth: () =>
    set({
      accessToken: null,
      trainer: null,
      trainerTypes: [],
      isAuthenticated: false,
    }),
}))
