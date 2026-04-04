import axios from 'axios'
import type { ApiError } from '@/types/api'
import { useAuthStore } from '@/stores/useAuthStore'

const correlationId = crypto.randomUUID()

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  headers: {
    'Content-Type': 'application/json',
    'X-Client-App': 'web-pulse',
    'X-Correlation-Id': correlationId,
  },
})

// Attach Authorization header from auth store
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Handle error responses
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response) {
      const { status, data } = error.response

      if (status === 401) {
        useAuthStore.getState().clearAuth()
        const currentPath = window.location.pathname
        if (currentPath !== '/auth/login') {
          window.location.href = `/auth/login?redirect=${encodeURIComponent(currentPath)}`
        }
      }

      // Parse RFC 7807 error if available
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
