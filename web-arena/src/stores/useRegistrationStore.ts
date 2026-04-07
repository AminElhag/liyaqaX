import { create } from 'zustand'

export interface ProfileFormData {
  nameAr: string
  nameEn: string
  email: string
  dateOfBirth: string
  gender: string
  emergencyContactName: string
  emergencyContactPhone: string
}

interface RegistrationState {
  registrationToken: string | null
  phone: string | null
  step: 1 | 2 | 3
  profileData: ProfileFormData | null
  setToken: (token: string, phone: string) => void
  setProfile: (data: ProfileFormData) => void
  advance: () => void
  goBack: () => void
  reset: () => void
}

export const useRegistrationStore = create<RegistrationState>()((set, get) => ({
  registrationToken: null,
  phone: null,
  step: 1,
  profileData: null,

  setToken: (token, phone) =>
    set({ registrationToken: token, phone, step: 2 }),

  setProfile: (data) =>
    set({ profileData: data, step: 3 }),

  advance: () => {
    const current = get().step
    if (current < 3) set({ step: (current + 1) as 1 | 2 | 3 })
  },

  goBack: () => {
    const current = get().step
    if (current > 1) set({ step: (current - 1) as 1 | 2 | 3 })
  },

  reset: () =>
    set({
      registrationToken: null,
      phone: null,
      step: 1,
      profileData: null,
    }),
}))
