import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { format, startOfWeek, addDays } from 'date-fns'
import { GXClassCard } from './GXClassCard'
import type { GXClassInstance } from '@/types/domain'

interface GXScheduleGridProps {
  instances: GXClassInstance[]
  weekStart: Date
}

const DAYS_IN_WEEK = 7

export function GXScheduleGrid({ instances, weekStart }: GXScheduleGridProps) {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'

  const weekDays = useMemo(() => {
    const start = startOfWeek(weekStart, { weekStartsOn: 0 })
    return Array.from({ length: DAYS_IN_WEEK }, (_, i) => addDays(start, i))
  }, [weekStart])

  const instancesByDay = useMemo(() => {
    const map = new Map<string, GXClassInstance[]>()
    for (const day of weekDays) {
      map.set(format(day, 'yyyy-MM-dd'), [])
    }
    for (const instance of instances) {
      const dayKey = format(new Date(instance.scheduledAt), 'yyyy-MM-dd')
      const dayInstances = map.get(dayKey)
      if (dayInstances) {
        dayInstances.push(instance)
      }
    }
    return map
  }, [instances, weekDays])

  return (
    <div className="overflow-x-auto">
      <div className="grid min-w-[800px] grid-cols-7 gap-2">
        {weekDays.map((day) => {
          const dayKey = format(day, 'yyyy-MM-dd')
          const dayInstances = instancesByDay.get(dayKey) ?? []
          const sortedInstances = [...dayInstances].sort(
            (a, b) =>
              new Date(a.scheduledAt).getTime() -
              new Date(b.scheduledAt).getTime(),
          )

          return (
            <div key={dayKey} className="min-h-[200px]">
              <div className="mb-2 text-center">
                <p className="text-sm font-medium">
                  {format(day, 'EEEE', {
                    locale: isAr ? undefined : undefined,
                  })}
                </p>
                <p className="text-xs text-gray-500">
                  {format(day, 'dd/MM')}
                </p>
              </div>
              <div className="space-y-2">
                {sortedInstances.length === 0 ? (
                  <p className="py-8 text-center text-xs text-gray-400">
                    {t('gx.schedule.noClasses')}
                  </p>
                ) : (
                  sortedInstances.map((instance) => (
                    <GXClassCard key={instance.id} instance={instance} />
                  ))
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
