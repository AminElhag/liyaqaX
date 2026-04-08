import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getGxClasses } from '@/api/gx'
import { format, addDays } from 'date-fns'

export const Route = createFileRoute('/gx/')({
  component: GxClassesPage,
})

function GxClassesPage() {
  const { t } = useTranslation()
  const today = format(new Date(), 'yyyy-MM-dd')
  const weekEnd = format(addDays(new Date(), 7), 'yyyy-MM-dd')

  const { data: classes = [], isLoading } = useQuery({
    queryKey: ['gx-classes', today, weekEnd],
    queryFn: () => getGxClasses(today, weekEnd),
  })

  return (
    <div>
      <h1 className="mb-4 text-xl font-bold text-gray-900">{t('gx.title')}</h1>

      {isLoading && <p className="text-sm text-gray-500">{t('common.loading')}</p>}

      {!isLoading && classes.length === 0 && (
        <p className="py-12 text-center text-sm text-gray-500">{t('gx.empty')}</p>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {classes.map((cls) => (
          <Link
            key={cls.id}
            to="/gx/$classId"
            params={{ classId: cls.id }}
            className="rounded-lg border border-gray-200 bg-white p-4 transition hover:shadow-md"
          >
            <div className="mb-2 flex items-center gap-2">
              <span
                className="inline-block h-3 w-3 rounded-full"
                style={{ backgroundColor: cls.classType.color ?? '#8B5CF6' }}
              />
              <span className="text-sm font-semibold text-gray-900">{cls.classType.name}</span>
            </div>
            <p className="text-xs text-gray-500">
              {new Date(cls.startTime).toLocaleDateString()} {' '}
              {new Date(cls.startTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
              {' - '}
              {new Date(cls.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
            </p>
            <div className="mt-2 flex items-center gap-3 text-xs text-gray-500">
              <span>{cls.bookedCount}/{cls.capacity} booked</span>
              <span>{cls.attendedCount} attended</span>
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}
