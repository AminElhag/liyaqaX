import { createFileRoute, Link } from '@tanstack/react-router'
import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { apiClient } from '@/api/client'
import { useAuthStore } from '@/stores/useAuthStore'
import { checkRegistrationEnabled } from '@/api/register'
import type { OtpVerifyResponse, MemberMe, PortalSettings } from '@/types/domain'
import type { ApiError } from '@/types/api'

export const Route = createFileRoute('/auth/login')({
  component: LoginPage,
})

const CLUB_ID = import.meta.env.VITE_CLUB_ID || ''

function LoginPage() {
  const [selfRegEnabled, setSelfRegEnabled] = useState(false)

  useEffect(() => {
    if (CLUB_ID) {
      checkRegistrationEnabled(CLUB_ID)
        .then(setSelfRegEnabled)
        .catch(() => setSelfRegEnabled(false))
    }
  }, [])
  const [step, setStep] = useState<'phone' | 'otp'>('phone')
  const [phone, setPhone] = useState('+966')
  const [otp, setOtp] = useState(['', '', '', '', '', ''])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [countdown, setCountdown] = useState(0)
  const { t } = useTranslation()
  const { setAuth, setMember, setPortalSettings } = useAuthStore()
  const otpRefs = useRef<(HTMLInputElement | null)[]>([])

  useEffect(() => {
    if (countdown <= 0) return
    const timer = setTimeout(() => setCountdown(countdown - 1), 1000)
    return () => clearTimeout(timer)
  }, [countdown])

  const handleRequestOtp = async () => {
    setError('')
    setLoading(true)
    try {
      await apiClient.post('/arena/auth/otp/request', { phone })
      setStep('otp')
      setCountdown(60)
    } catch (err) {
      const apiErr = err as ApiError
      setError(apiErr?.detail || t('common.error'))
    } finally {
      setLoading(false)
    }
  }

  const handleVerifyOtp = async () => {
    setError('')
    setLoading(true)
    try {
      const code = otp.join('')
      const { data } = await apiClient.post<OtpVerifyResponse>('/arena/auth/otp/verify', { phone, otp: code })
      setAuth(data.accessToken)

      // Fetch member profile and portal settings
      const [meRes, settingsRes] = await Promise.all([
        apiClient.get<MemberMe>('/arena/me', { headers: { Authorization: `Bearer ${data.accessToken}` } }),
        apiClient.get<PortalSettings>('/arena/portal-settings', { headers: { Authorization: `Bearer ${data.accessToken}` } }),
      ])
      setMember(meRes.data)
      setPortalSettings(settingsRes.data)

      // Navigate based on language preference
      if (!data.member.preferredLanguage) {
        window.location.href = '/auth/language'
      } else {
        window.location.href = '/'
      }
    } catch (err) {
      const apiErr = err as ApiError
      setError(apiErr?.detail || t('auth.otp.invalid'))
    } finally {
      setLoading(false)
    }
  }

  const handleOtpChange = (index: number, value: string) => {
    if (!/^\d?$/.test(value)) return
    const newOtp = [...otp]
    newOtp[index] = value
    setOtp(newOtp)
    if (value && index < 5) {
      otpRefs.current[index + 1]?.focus()
    }
  }

  const handleOtpKeyDown = (index: number, e: React.KeyboardEvent) => {
    if (e.key === 'Backspace' && !otp[index] && index > 0) {
      otpRefs.current[index - 1]?.focus()
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm">
        {step === 'phone' ? (
          <div className="space-y-6 text-center">
            <h1 className="text-2xl font-bold">{t('auth.phone.title')}</h1>
            <p className="text-gray-500">{t('auth.phone.subtitle')}</p>
            <input
              type="tel"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="+966501234567"
              className="w-full rounded-lg border px-4 py-3 text-lg"
              dir="ltr"
            />
            {error && <p className="text-sm text-red-500">{error}</p>}
            <button
              onClick={handleRequestOtp}
              disabled={loading || phone.length < 13}
              className="w-full rounded-lg bg-blue-600 py-3 font-semibold text-white disabled:opacity-50"
            >
              {loading ? t('common.loading') : t('auth.phone.sendCode')}
            </button>
            {selfRegEnabled && (
              <Link to="/auth/register" className="block text-sm text-blue-600 hover:underline">
                {t('register.link')}
              </Link>
            )}
          </div>
        ) : (
          <div className="space-y-6 text-center">
            <h1 className="text-2xl font-bold">{t('auth.otp.title')}</h1>
            <p className="text-gray-500">{t('auth.otp.subtitle', { phone })}</p>
            <div className="flex justify-center gap-2" dir="ltr">
              {otp.map((digit, i) => (
                <input
                  key={i}
                  ref={(el) => { otpRefs.current[i] = el }}
                  type="text"
                  inputMode="numeric"
                  maxLength={1}
                  value={digit}
                  onChange={(e) => handleOtpChange(i, e.target.value)}
                  onKeyDown={(e) => handleOtpKeyDown(i, e)}
                  className="h-12 w-12 rounded-lg border text-center text-xl"
                />
              ))}
            </div>
            {error && <p className="text-sm text-red-500">{error}</p>}
            <button
              onClick={handleVerifyOtp}
              disabled={loading || otp.some(d => !d)}
              className="w-full rounded-lg bg-blue-600 py-3 font-semibold text-white disabled:opacity-50"
            >
              {loading ? t('common.loading') : t('auth.otp.verify')}
            </button>
            <button
              onClick={handleRequestOtp}
              disabled={countdown > 0 || loading}
              className="text-sm text-blue-600 disabled:text-gray-400"
            >
              {countdown > 0 ? t('auth.otp.resendIn', { seconds: countdown }) : t('auth.otp.resend')}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
