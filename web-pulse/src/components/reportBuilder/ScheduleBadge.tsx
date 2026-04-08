import { useTranslation } from 'react-i18next'
import type { ReportScheduleResponse } from '@/api/reportSchedules'

interface ScheduleBadgeProps {
  schedule: ReportScheduleResponse | null | undefined
}

export function ScheduleBadge({ schedule }: ScheduleBadgeProps) {
  const { t } = useTranslation()

  if (!schedule) {
    return null
  }

  if (!schedule.isActive) {
    return (
      <span className="inline-flex items-center rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700">
        {t('reports.schedule.paused')}
      </span>
    )
  }

  if (!schedule.lastRunAt) {
    return (
      <span className="inline-flex items-center rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600">
        {t('reports.schedule.neverRun')}
      </span>
    )
  }

  if (schedule.lastRunStatus === 'failed') {
    return (
      <span className="inline-flex items-center rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700">
        {t('reports.schedule.failed')}
      </span>
    )
  }

  return (
    <span className="inline-flex items-center rounded-full bg-green-50 px-2 py-0.5 text-xs font-medium text-green-700">
      {t('reports.schedule.active')}
    </span>
  )
}
