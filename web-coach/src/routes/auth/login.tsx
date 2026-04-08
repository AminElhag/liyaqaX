import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { login } from '@/api/auth'
import { getMe } from '@/api/profile'
import { useAuthStore } from '@/stores/useAuthStore'

export const Route = createFileRoute('/auth/login')({
  component: LoginPage,
})

function LoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const setAuth = useAuthStore((s) => s.setAuth)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)

    try {
      const res = await login(email, password)

      if (res.scope !== 'trainer') {
        setError(t('auth.login.scopeError'))
        setLoading(false)
        return
      }

      // Temporarily set token so the profile API call works
      useAuthStore.setState({ accessToken: res.accessToken })
      const profile = await getMe()
      setAuth(res.accessToken, profile)
      navigate({ to: '/' })
    } catch {
      setError(t('auth.login.error'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="w-full max-w-sm rounded-lg bg-white p-8 shadow-md">
        <h1 className="mb-6 text-center text-2xl font-bold text-gray-900">
          {t('auth.login.title')}
        </h1>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="email" className="mb-1 block text-sm font-medium text-gray-700">
              {t('auth.login.email')}
            </label>
            <input
              id="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500"
            />
          </div>

          <div>
            <label htmlFor="password" className="mb-1 block text-sm font-medium text-gray-700">
              {t('auth.login.password')}
            </label>
            <input
              id="password"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-teal-500 focus:outline-none focus:ring-1 focus:ring-teal-500"
            />
          </div>

          {error && (
            <p className="rounded-md bg-red-50 p-3 text-sm text-red-600">{error}</p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-md bg-teal-600 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
          >
            {loading ? t('common.loading') : t('auth.login.submit')}
          </button>
        </form>
      </div>
    </div>
  )
}
