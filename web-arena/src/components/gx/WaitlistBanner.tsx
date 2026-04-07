import { useTranslation } from 'react-i18next'
import { formatDistanceToNow } from 'date-fns'

interface WaitlistBannerProps {
  offerExpiresAt: string
  onAccept: () => void
  isAccepting: boolean
}

export function WaitlistBanner({ offerExpiresAt, onAccept, isAccepting }: WaitlistBannerProps) {
  const { t } = useTranslation()
  const deadline = new Date(offerExpiresAt)
  const isExpired = deadline < new Date()
  const timeLeft = formatDistanceToNow(deadline, { addSuffix: true })

  if (isExpired) return null

  return (
    <div className="rounded-lg border border-amber-300 bg-amber-50 p-4">
      <p className="text-sm font-medium text-amber-800">
        {t('gx.waitlist.offered_banner', { deadline: timeLeft })}
      </p>
      <button
        type="button"
        onClick={onAccept}
        disabled={isAccepting}
        className="mt-2 rounded-md bg-amber-600 px-4 py-2 text-sm font-medium text-white hover:bg-amber-700 disabled:opacity-50"
      >
        {isAccepting ? t('common.loading') : t('gx.waitlist.accept')}
      </button>
    </div>
  )
}
