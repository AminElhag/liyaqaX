import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState, useEffect } from 'react'
import { getClassBookings, markGxAttendance } from '@/api/gx'

export const Route = createFileRoute('/gx/$classId')({
  component: GxClassDetailPage,
})

function GxClassDetailPage() {
  const { classId } = Route.useParams()
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const { data: bookings = [], isLoading } = useQuery({
    queryKey: ['gx-bookings', classId],
    queryFn: () => getClassBookings(classId),
  })

  const [attendanceMap, setAttendanceMap] = useState<Record<string, boolean>>({})

  useEffect(() => {
    const initial: Record<string, boolean> = {}
    bookings.forEach((b) => {
      initial[b.id] = b.attended ?? true
    })
    setAttendanceMap(initial)
  }, [bookings])

  const mutation = useMutation({
    mutationFn: () => {
      const attendances = Object.entries(attendanceMap).map(([bookingId, attended]) => ({
        bookingId,
        attended,
      }))
      return markGxAttendance(classId, attendances)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['gx-bookings', classId] })
      queryClient.invalidateQueries({ queryKey: ['gx-classes'] })
      queryClient.invalidateQueries({ queryKey: ['schedule'] })
    },
  })

  return (
    <div>
      <h1 className="mb-4 text-xl font-bold text-gray-900">{t('gx.bookings')}</h1>

      {isLoading && <p className="text-sm text-gray-500">{t('common.loading')}</p>}

      <div className="space-y-2">
        {bookings.map((booking) => (
          <label
            key={booking.id}
            className="flex items-center gap-3 rounded-lg border border-gray-200 bg-white p-3"
          >
            <input
              type="checkbox"
              checked={attendanceMap[booking.id] ?? true}
              onChange={(e) =>
                setAttendanceMap((prev) => ({ ...prev, [booking.id]: e.target.checked }))
              }
              className="h-4 w-4 rounded border-gray-300 text-teal-600 focus:ring-teal-500"
            />
            <span className="text-sm text-gray-900">{booking.memberName}</span>
            <span className="ms-auto text-xs text-gray-400">
              {new Date(booking.bookedAt).toLocaleDateString()}
            </span>
          </label>
        ))}
      </div>

      {bookings.length > 0 && (
        <button
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending}
          className="mt-4 rounded-md bg-teal-600 px-6 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
        >
          {mutation.isPending ? t('common.loading') : t('gx.save_attendance')}
        </button>
      )}
    </div>
  )
}
