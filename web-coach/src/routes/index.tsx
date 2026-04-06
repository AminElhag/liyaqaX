import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { getSchedule } from '@/api/schedule'
import { format } from 'date-fns'
import { cn } from '@/lib/cn'

export const Route = createFileRoute('/')({
  component: SchedulePage,
})

function SchedulePage() {
  const { t } = useTranslation()
  const [date, setDate] = useState(() => format(new Date(), 'yyyy-MM-dd'))

  const { data: items = [], isLoading } = useQuery({
    queryKey: ['schedule', date],
    queryFn: () => getSchedule(date),
    refetchInterval: 60_000,
  })

  const maxDate = format(
    new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
    'yyyy-MM-dd',
  )

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">{t('schedule.title')}</h1>
        <input
          type="date"
          value={date}
          max={maxDate}
          onChange={(e) => setDate(e.target.value)}
          className="rounded-md border border-gray-300 px-3 py-1.5 text-sm"
        />
      </div>

      {isLoading && <p className="text-sm text-gray-500">{t('common.loading')}</p>}

      {!isLoading && items.length === 0 && (
        <p className="py-12 text-center text-sm text-gray-500">{t('schedule.empty')}</p>
      )}

      <div className="space-y-3">
        {items.map((item) => (
          <div
            key={item.id}
            className="flex items-center gap-4 rounded-lg border border-gray-200 bg-white p-4"
          >
            <span
              className={cn(
                'inline-flex rounded-full px-2 py-0.5 text-xs font-semibold uppercase',
                item.type === 'pt'
                  ? 'bg-teal-100 text-teal-800'
                  : 'bg-purple-100 text-purple-800',
              )}
            >
              {item.type}
            </span>
            <div className="flex-1">
              <p className="text-sm font-medium text-gray-900">{item.title}</p>
              <p className="text-xs text-gray-500">{item.memberOrClassName}</p>
            </div>
            <div className="text-end">
              <p className="text-sm text-gray-700">
                {new Date(item.startTime).toLocaleTimeString([], {
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </p>
              <span
                className={cn(
                  'text-xs font-medium',
                  item.status === 'scheduled' && 'text-blue-600',
                  item.status === 'attended' && 'text-green-600',
                  item.status === 'missed' && 'text-red-600',
                )}
              >
                {item.status}
              </span>
            </div>
            {item.type === 'gx' && item.capacity != null && (
              <span className="text-xs text-gray-400">
                {item.bookedCount}/{item.capacity}
              </span>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
