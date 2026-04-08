import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState, useRef, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useRegistrationStore } from '@/stores/useRegistrationStore'
import {
  requestRegistrationOtp,
  verifyRegistrationOtp,
  completeRegistration,
} from '@/api/register'
import type { ApiError } from '@/types/api'
import type { ProfileFormData } from '@/stores/useRegistrationStore'

export const Route = createFileRoute('/auth/register/')({
  component: RegisterPage,
})

const CLUB_ID = import.meta.env.VITE_CLUB_ID || ''

function RegisterPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const {
    step, registrationToken, phone: storedPhone,
    setToken, setProfile, profileData, reset,
  } = useRegistrationStore()

  if (step === 1) return <PhoneStep clubId={CLUB_ID} />
  if (step === 2) return <ProfileStep />
  if (step === 3) return <PlanStep clubId={CLUB_ID} />
  return null
}

function StepIndicator({ current }: { current: number }) {
  const { t } = useTranslation()
  return (
    <div className="mb-6 text-center text-sm text-gray-500">
      {t('register.step', { current, total: 3 })}
      <div className="mt-2 flex justify-center gap-2">
        {[1, 2, 3].map((s) => (
          <div
            key={s}
            className={`h-2 w-8 rounded-full ${s <= current ? 'bg-primary' : 'bg-gray-200'}`}
          />
        ))}
      </div>
    </div>
  )
}

function PhoneStep({ clubId }: { clubId: string }) {
  const { t } = useTranslation()
  const [phone, setPhone] = useState('+966')
  const [otpStep, setOtpStep] = useState(false)
  const [otp, setOtp] = useState(['', '', '', '', '', ''])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [countdown, setCountdown] = useState(0)
  const { setToken } = useRegistrationStore()
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
      await requestRegistrationOtp(phone, clubId)
      setOtpStep(true)
      setCountdown(60)
    } catch (err) {
      const apiErr = err as { response?: { data?: ApiError } }
      setError(apiErr?.response?.data?.detail || t('common.error'))
    } finally {
      setLoading(false)
    }
  }

  const handleVerifyOtp = async () => {
    setError('')
    setLoading(true)
    try {
      const code = otp.join('')
      const token = await verifyRegistrationOtp(phone, code, clubId)
      setToken(token, phone)
    } catch (err) {
      const apiErr = err as { response?: { data?: ApiError } }
      const detail = apiErr?.response?.data?.detail || ''
      if (detail.includes('already registered')) {
        setError(t('register.error.phoneRegistered'))
      } else if (detail.includes('pending')) {
        setError(t('register.error.pendingReview'))
      } else {
        setError(detail || t('auth.otp.invalid'))
      }
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
        <StepIndicator current={1} />
        {!otpStep ? (
          <div className="space-y-6 text-center">
            <h1 className="text-2xl font-bold">{t('register.phone.title')}</h1>
            <p className="text-gray-500">{t('register.phone.subtitle')}</p>
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
              className="w-full rounded-lg bg-primary py-3 font-semibold text-white disabled:opacity-50"
            >
              {loading ? t('common.loading') : t('auth.phone.sendCode')}
            </button>
            <a href="/auth/login" className="block text-sm text-primary">
              {t('auth.phone.title')}
            </a>
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
              className="w-full rounded-lg bg-primary py-3 font-semibold text-white disabled:opacity-50"
            >
              {loading ? t('common.loading') : t('auth.otp.verify')}
            </button>
            <button
              onClick={handleRequestOtp}
              disabled={countdown > 0 || loading}
              className="text-sm text-primary disabled:text-gray-400"
            >
              {countdown > 0 ? t('auth.otp.resendIn', { seconds: countdown }) : t('auth.otp.resend')}
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

function ProfileStep() {
  const { t } = useTranslation()
  const { setProfile, goBack, registrationToken } = useRegistrationStore()
  const navigate = useNavigate()
  const [form, setForm] = useState<ProfileFormData>({
    nameAr: '', nameEn: '', email: '', dateOfBirth: '',
    gender: '', emergencyContactName: '', emergencyContactPhone: '',
  })
  const [error, setError] = useState('')

  if (!registrationToken) {
    navigate({ to: '/auth/register' })
    return null
  }

  const handleSubmit = () => {
    if (!form.nameAr.trim() && !form.nameEn.trim()) {
      setError(t('register.error.nameRequired'))
      return
    }
    setProfile(form)
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm">
        <StepIndicator current={2} />
        <div className="space-y-4">
          <h1 className="text-center text-2xl font-bold">{t('register.profile.title')}</h1>
          <input
            type="text"
            value={form.nameAr}
            onChange={(e) => setForm({ ...form, nameAr: e.target.value })}
            placeholder={t('register.profile.nameAr')}
            className="w-full rounded-lg border px-4 py-3"
            dir="rtl"
          />
          <input
            type="text"
            value={form.nameEn}
            onChange={(e) => setForm({ ...form, nameEn: e.target.value })}
            placeholder={t('register.profile.nameEn')}
            className="w-full rounded-lg border px-4 py-3"
          />
          <input
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            placeholder={t('register.profile.email')}
            className="w-full rounded-lg border px-4 py-3"
          />
          <input
            type="date"
            value={form.dateOfBirth}
            onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value })}
            placeholder={t('register.profile.dob')}
            className="w-full rounded-lg border px-4 py-3"
          />
          <div className="flex gap-4">
            <label className="flex items-center gap-2">
              <input
                type="radio"
                name="gender"
                value="male"
                checked={form.gender === 'male'}
                onChange={() => setForm({ ...form, gender: 'male' })}
              />
              {t('register.profile.genderMale')}
            </label>
            <label className="flex items-center gap-2">
              <input
                type="radio"
                name="gender"
                value="female"
                checked={form.gender === 'female'}
                onChange={() => setForm({ ...form, gender: 'female' })}
              />
              {t('register.profile.genderFemale')}
            </label>
          </div>
          <div className="border-t pt-4">
            <p className="mb-2 text-sm font-medium text-gray-600">{t('register.profile.emergency')}</p>
            <input
              type="text"
              value={form.emergencyContactName}
              onChange={(e) => setForm({ ...form, emergencyContactName: e.target.value })}
              placeholder={t('register.profile.emergencyName')}
              className="mb-2 w-full rounded-lg border px-4 py-3"
            />
            <input
              type="tel"
              value={form.emergencyContactPhone}
              onChange={(e) => setForm({ ...form, emergencyContactPhone: e.target.value })}
              placeholder={t('register.profile.emergencyPhone')}
              className="w-full rounded-lg border px-4 py-3"
              dir="ltr"
            />
          </div>
          {error && <p className="text-sm text-red-500">{error}</p>}
          <div className="flex gap-3">
            <button
              onClick={goBack}
              className="flex-1 rounded-lg border py-3 font-semibold"
            >
              {t('register.profile.back')}
            </button>
            <button
              onClick={handleSubmit}
              className="flex-1 rounded-lg bg-primary py-3 font-semibold text-white"
            >
              {t('register.profile.next')}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

function PlanStep({ clubId }: { clubId: string }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { registrationToken, profileData, reset } = useRegistrationStore()
  const [plans, setPlans] = useState<Array<{ id: string; nameAr: string; nameEn: string; priceSar: string; durationDays: number }>>([])
  const [selectedPlan, setSelectedPlan] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!registrationToken) {
      navigate({ to: '/auth/register' })
      return
    }
    // Fetch available plans (public endpoint)
    fetch(`${import.meta.env.VITE_API_BASE_URL || '/api/v1'}/arena/register/check?clubId=${clubId}`)
      .catch(() => {})
  }, [registrationToken, navigate, clubId])

  const handleComplete = async (planId: string | null) => {
    if (!registrationToken || !profileData) return
    setLoading(true)
    setError('')
    try {
      await completeRegistration(registrationToken, {
        nameAr: profileData.nameAr || undefined,
        nameEn: profileData.nameEn || undefined,
        email: profileData.email || undefined,
        dateOfBirth: profileData.dateOfBirth || undefined,
        gender: profileData.gender || undefined,
        emergencyContactName: profileData.emergencyContactName || undefined,
        emergencyContactPhone: profileData.emergencyContactPhone || undefined,
        desiredMembershipPlanId: planId || undefined,
      })
      reset()
      navigate({ to: '/auth/register/success' })
    } catch (err) {
      const apiErr = err as { response?: { data?: ApiError } }
      setError(apiErr?.response?.data?.detail || t('common.error'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm">
        <StepIndicator current={3} />
        <div className="space-y-6 text-center">
          <h1 className="text-2xl font-bold">{t('register.plan.title')}</h1>
          <p className="text-gray-500">{t('register.plan.subtitle')}</p>

          {error && <p className="text-sm text-red-500">{error}</p>}

          <button
            onClick={() => handleComplete(null)}
            disabled={loading}
            className="w-full rounded-lg border-2 border-dashed border-gray-300 py-4 text-gray-500 hover:border-primary hover:text-primary disabled:opacity-50"
          >
            {loading ? t('common.loading') : t('register.plan.skip')}
          </button>
        </div>
      </div>
    </div>
  )
}
