import { useTranslation } from 'react-i18next'
import { formatDate } from '@/lib/formatDate'
import type { Membership } from '@/types/domain'

interface MembershipStatusTimelineProps {
  membership: Membership
}

interface TimelineEvent {
  date: string
  labelKey: string
  color: 'green' | 'orange' | 'amber'
}

export function MembershipStatusTimeline({ membership }: MembershipStatusTimelineProps) {
  const { t, i18n } = useTranslation()
  const locale = i18n.language

  const events: TimelineEvent[] = [
    {
      date: membership.startDate,
      labelKey: 'membership.startDate',
      color: 'green',
    },
    {
      date: membership.endDate,
      labelKey: 'membership.endDate',
      color: 'orange',
    },
  ]

  if (membership.graceEndDate) {
    events.push({
      date: membership.graceEndDate,
      labelKey: 'membership.graceEndDate',
      color: 'amber',
    })
  }

  const dotColors: Record<string, string> = {
    green: 'bg-green-500',
    orange: 'bg-orange-500',
    amber: 'bg-amber-500',
  }

  const lineColors: Record<string, string> = {
    green: 'bg-green-300',
    orange: 'bg-orange-300',
    amber: 'bg-amber-300',
  }

  return (
    <div className="mt-4">
      <h4 className="mb-3 text-sm font-medium text-gray-700">{t('membership.timeline')}</h4>
      <div className="relative">
        {events.map((event, index) => (
          <div key={event.labelKey} className="flex items-start pb-6 last:pb-0">
            <div className="relative flex flex-col items-center">
              <div
                className={`h-3 w-3 rounded-full ${dotColors[event.color]} ring-2 ring-white`}
              />
              {index < events.length - 1 && (
                <div
                  className={`mt-1 h-full w-0.5 ${lineColors[events[index + 1].color]}`}
                  style={{ minHeight: '2rem' }}
                />
              )}
            </div>
            <div className="ms-3">
              <p className="text-sm font-medium text-gray-900">
                {formatDate(event.date, locale)}
              </p>
              <p className="text-xs text-gray-500">{t(event.labelKey)}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
