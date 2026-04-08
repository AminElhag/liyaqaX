import axios from 'axios'
import type { ApiError } from '@/types/api'
import { useAuthStore } from '@/stores/useAuthStore'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  headers: {
    'Content-Type': 'application/json',
    'X-Client-App': 'web-arena',
  },
})

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response) {
      const { status, data } = error.response
      if (status === 401) {
        useAuthStore.getState().clearAuth()
        if (window.location.pathname !== '/auth/login') {
          window.location.href = '/auth/login'
        }
      }
      const apiError: ApiError = {
        type: data?.type ?? 'about:blank',
        title: data?.title ?? 'Error',
        status,
        detail: data?.detail ?? error.message,
        instance: data?.instance ?? '',
      }
      return Promise.reject(apiError)
    }
    return Promise.reject(error)
  },
)
