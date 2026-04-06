import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { login, getMe } from '@/api/auth'
import { useAuthStore } from '@/stores/useAuthStore'
import { ALLOWED_SCOPES } from '@/types/permissions'
import type { ApiError } from '@/types/api'

interface LoginSearch {
  redirect?: string
}

export const Route = createFileRoute('/auth/login')({
  validateSearch: (search: Record<string, unknown>): LoginSearch => ({
    redirect: (search.redirect as string) || undefined,
  }),
  component: LoginPage,
})

const loginSchema = z.object({
  email: z.string().min(1).email(),
  password: z.string().min(1),
})

type LoginForm = z.infer<typeof loginSchema>

function LoginPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { redirect } = Route.useSearch()
  const setAuth = useAuthStore((s) => s.setAuth)
  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
  })

  const onSubmit = async (data: LoginForm) => {
    setError(null)
    try {
      const loginRes = await login(data.email, data.password)

      // Scope check before storing auth
      if (!ALLOWED_SCOPES.includes(loginRes.scope as (typeof ALLOWED_SCOPES)[number])) {
        setError(t('login.scope_error'))
        return
      }

      // Store token temporarily to make the getMe call
      useAuthStore.getState().setAuth(
        loginRes.accessToken,
        {
          id: loginRes.userId,
          email: data.email,
          scope: loginRes.scope,
          roleId: loginRes.roleId,
          roleName: loginRes.roleName,
          organizationId: loginRes.organizationId,
          clubId: loginRes.clubId,
          branchIds: loginRes.branchIds ?? [],
        },
        [],
      )

      // Fetch permissions
      const me = await getMe()

      // Update with full permissions
      setAuth(
        loginRes.accessToken,
        {
          id: me.userId,
          email: me.email,
          scope: me.scope,
          roleId: me.roleId,
          roleName: me.roleName,
          organizationId: me.organizationId,
          clubId: me.clubId,
          branchIds: me.branchIds,
        },
        me.permissions,
      )

      navigate({ to: redirect || '/' })
    } catch (err) {
      const apiError = err as ApiError
      setError(apiError.detail || t('login.invalid'))
    }
  }

  const toggleLanguage = () => {
    i18n.changeLanguage(i18n.language === 'ar' ? 'en' : 'ar')
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm">
        <div className="rounded-lg bg-white p-8 shadow-md">
          <div className="mb-6 text-center">
            <h1 className="text-2xl font-bold text-gray-900">Nexus</h1>
            <p className="mt-1 text-sm text-gray-500">
              {t('login.title')}
            </p>
          </div>

          {error && (
            <div
              role="alert"
              className="mb-4 rounded-md bg-red-50 p-3 text-sm text-red-700"
            >
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div>
              <label
                htmlFor="email"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                {t('login.email')}
              </label>
              <input
                id="email"
                type="email"
                autoComplete="email"
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500 focus:outline-none"
                {...register('email')}
              />
              {errors.email && (
                <p className="mt-1 text-xs text-red-600">
                  {errors.email.message}
                </p>
              )}
            </div>

            <div>
              <label
                htmlFor="password"
                className="mb-1 block text-sm font-medium text-gray-700"
              >
                {t('login.password')}
              </label>
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500 focus:outline-none"
                {...register('password')}
              />
              {errors.password && (
                <p className="mt-1 text-xs text-red-600">
                  {errors.password.message}
                </p>
              )}
            </div>

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700 focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 focus:outline-none disabled:cursor-not-allowed disabled:opacity-50"
            >
              {isSubmitting ? t('common.loading') : t('login.submit')}
            </button>
          </form>
        </div>

        <div className="mt-4 text-center">
          <button
            type="button"
            onClick={toggleLanguage}
            className="text-sm text-gray-500 hover:text-gray-700"
          >
            {i18n.language === 'ar' ? 'English' : '\u0627\u0644\u0639\u0631\u0628\u064A\u0629'}
          </button>
        </div>
      </div>
    </div>
  )
}
