import { create } from 'zustand'

interface GraceState {
  isGrace: boolean
  daysRemaining: number
  setGrace: (isGrace: boolean, daysRemaining: number) => void
}

export const useGraceStore = create<GraceState>()((set) => ({
  isGrace: false,
  daysRemaining: 0,
  setGrace: (isGrace, daysRemaining) => set({ isGrace, daysRemaining }),
}))
