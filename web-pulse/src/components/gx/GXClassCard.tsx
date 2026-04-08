import { useTranslation } from 'react-i18next'
import { Link } from '@tanstack/react-router'
import { format } from 'date-fns'
import { GXCapacityBar } from './GXCapacityBar'
import { GXStatusBadge } from './GXStatusBadge'
import type { GXClassInstance } from '@/types/domain'

interface GXClassCardProps {
  instance: GXClassInstance
}

export function GXClassCard({ instance }: GXClassCardProps) {
  const { i18n } = useTranslation()
  const isAr = i18n.language === 'ar'
  const className = isAr ? instance.classType.nameAr : instance.classType.nameEn
  const instructorName = isAr
    ? `${instance.instructor.firstNameAr} ${instance.instructor.lastNameAr}`
    : `${instance.instructor.firstNameEn} ${instance.instructor.lastNameEn}`

  const scheduledDate = new Date(instance.scheduledAt)
  const timeStr = format(scheduledDate, 'HH:mm')

  return (
    <Link
      to="/gx/$classId"
      params={{ classId: instance.id }}
      className="block rounded-lg border p-3 transition-colors hover:bg-gray-50"
      style={{
        borderInlineStartWidth: '4px',
        borderInlineStartColor: instance.classType.color ?? '#6B7280',
      }}
    >
      <div className="flex items-start justify-between">
        <div>
          <p className="font-medium">{className}</p>
          <p className="text-sm text-gray-500">{instructorName}</p>
        </div>
        <GXStatusBadge status={instance.status} />
      </div>
      <div className="mt-2 flex items-center justify-between text-sm text-gray-500">
        <span>{timeStr} &middot; {instance.durationMinutes}m</span>
        {instance.room && <span>{instance.room}</span>}
      </div>
      <div className="mt-2 flex items-center gap-2">
        <div className="flex-1">
          <GXCapacityBar
            bookingsCount={instance.bookingsCount}
            capacity={instance.capacity}
          />
        </div>
        {instance.waitlistCount > 0 && (
          <span className="shrink-0 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">
            {instance.waitlistCount} WL
          </span>
        )}
      </div>
    </Link>
  )
}
