import { useState } from 'react'
import { useTranslation } from 'react-i18next'

const MAX_RECIPIENTS = 10
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

interface ScheduleFormProps {
  initialFrequency?: string
  initialRecipients?: string[]
  initialIsActive?: boolean
  onSubmit: (data: { frequency: string; recipients: string[]; isActive: boolean }) => void
  isPending: boolean
  submitLabel: string
}

function computeNextRun(frequency: string): string {
  const now = new Date()
  const riyadhOffset = 3 * 60
  const utcMs = now.getTime() + now.getTimezoneOffset() * 60000
  const riyadhNow = new Date(utcMs + riyadhOffset * 60000)

  const todayAt7 = new Date(riyadhNow)
  todayAt7.setHours(7, 0, 0, 0)

  const hasTodayRun = riyadhNow >= todayAt7

  if (frequency === 'daily') {
    const next = new Date(todayAt7)
    if (hasTodayRun) next.setDate(next.getDate() + 1)
    return next.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })
  }

  if (frequency === 'weekly') {
    const next = new Date(riyadhNow)
    const dayOfWeek = next.getDay()
    const daysUntilMonday = dayOfWeek === 1 && !hasTodayRun ? 0 : ((8 - dayOfWeek) % 7 || 7)
    next.setDate(next.getDate() + daysUntilMonday)
    return next.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })
  }

  if (frequency === 'monthly') {
    const next = new Date(riyadhNow)
    if (next.getDate() === 1 && !hasTodayRun) {
      return next.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })
    }
    next.setMonth(next.getMonth() + 1, 1)
    return next.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' })
  }

  return ''
}

export function ScheduleForm({
  initialFrequency = 'daily',
  initialRecipients = [],
  initialIsActive = true,
  onSubmit,
  isPending,
  submitLabel,
}: ScheduleFormProps) {
  const { t } = useTranslation()
  const [frequency, setFrequency] = useState(initialFrequency)
  const [recipients, setRecipients] = useState<string[]>(initialRecipients)
  const [emailInput, setEmailInput] = useState('')
  const [emailError, setEmailError] = useState<string | null>(null)
  const [isActive, setIsActive] = useState(initialIsActive)

  const addRecipient = () => {
    const trimmed = emailInput.trim()
    if (!trimmed) return

    if (!EMAIL_REGEX.test(trimmed)) {
      setEmailError(t('reports.schedule.invalidEmail'))
      return
    }

    if (recipients.includes(trimmed)) {
      setEmailError(t('reports.schedule.duplicateEmail'))
      return
    }

    if (recipients.length >= MAX_RECIPIENTS) {
      setEmailError(t('reports.schedule.maxRecipients'))
      return
    }

    setRecipients([...recipients, trimmed])
    setEmailInput('')
    setEmailError(null)
  }

  const removeRecipient = (email: string) => {
    setRecipients(recipients.filter((r) => r !== email))
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      addRecipient()
    }
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (recipients.length === 0) return
    onSubmit({ frequency, recipients, isActive })
  }

  const nextRun = computeNextRun(frequency)

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label className="block text-sm font-medium text-gray-700">
          {t('reports.schedule.frequency')}
        </label>
        <div className="mt-2 flex gap-3">
          {(['daily', 'weekly', 'monthly'] as const).map((f) => (
            <label key={f} className="flex items-center gap-1.5">
              <input
                type="radio"
                name="frequency"
                value={f}
                checked={frequency === f}
                onChange={() => setFrequency(f)}
                className="text-blue-600"
              />
              <span className="text-sm">{t(`reports.schedule.${f}`)}</span>
            </label>
          ))}
        </div>
        {nextRun && (
          <p className="mt-1 text-xs text-gray-500">
            {t('reports.schedule.nextRun')}: {nextRun}
          </p>
        )}
      </div>

      <div>
        <label className="block text-sm font-medium text-gray-700">
          {t('reports.schedule.recipients')}
        </label>
        <div className="mt-1 flex flex-wrap gap-1.5">
          {recipients.map((email) => (
            <span
              key={email}
              className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2 py-0.5 text-xs text-blue-700"
            >
              {email}
              <button
                type="button"
                onClick={() => removeRecipient(email)}
                className="text-blue-500 hover:text-blue-800"
              >
                x
              </button>
            </span>
          ))}
        </div>
        <div className="mt-1 flex gap-2">
          <input
            type="email"
            value={emailInput}
            onChange={(e) => {
              setEmailInput(e.target.value)
              setEmailError(null)
            }}
            onKeyDown={handleKeyDown}
            placeholder="email@example.com"
            disabled={recipients.length >= MAX_RECIPIENTS}
            className="flex-1 rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-blue-500 focus:outline-none disabled:bg-gray-100"
          />
          <button
            type="button"
            onClick={addRecipient}
            disabled={recipients.length >= MAX_RECIPIENTS || !emailInput.trim()}
            className="rounded-md bg-gray-100 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-200 disabled:opacity-50"
          >
            {t('reports.schedule.addRecipient')}
          </button>
        </div>
        {emailError && <p className="mt-1 text-xs text-red-600">{emailError}</p>}
        <p className="mt-1 text-xs text-gray-400">
          {recipients.length}/{MAX_RECIPIENTS} {t('reports.schedule.recipientsCount')}
        </p>
      </div>

      <label className="flex items-center gap-2">
        <input
          type="checkbox"
          checked={isActive}
          onChange={(e) => setIsActive(e.target.checked)}
          className="text-blue-600"
        />
        <span className="text-sm text-gray-700">{t('reports.schedule.isActive')}</span>
      </label>

      <button
        type="submit"
        disabled={isPending || recipients.length === 0}
        className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
      >
        {isPending ? t('reports.schedule.saving') : submitLabel}
      </button>
    </form>
  )
}
