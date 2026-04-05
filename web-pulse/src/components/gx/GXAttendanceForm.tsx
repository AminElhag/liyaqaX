import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { GXBooking, GXAttendanceStatus, AttendanceEntry } from '@/types/domain'

interface GXAttendanceFormProps {
  bookings: GXBooking[]
  existingAttendance?: Record<string, GXAttendanceStatus>
  onSubmit: (entries: AttendanceEntry[]) => void
  isSubmitting?: boolean
}

export function GXAttendanceForm({
  bookings,
  existingAttendance = {},
  onSubmit,
  isSubmitting = false,
}: GXAttendanceFormProps) {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'

  const confirmedBookings = bookings.filter(
    (b) => b.status === 'confirmed' || b.status === 'promoted',
  )

  const [attendance, setAttendance] = useState<Record<string, GXAttendanceStatus>>(() => {
    const initial: Record<string, GXAttendanceStatus> = {}
    for (const booking of confirmedBookings) {
      initial[booking.member.id] =
        existingAttendance[booking.member.id] ?? 'present'
    }
    return initial
  })

  function handleStatusChange(memberId: string, status: GXAttendanceStatus) {
    setAttendance((prev) => ({ ...prev, [memberId]: status }))
  }

  function handleSubmit() {
    const entries: AttendanceEntry[] = Object.entries(attendance).map(
      ([memberId, status]) => ({ memberId, status }),
    )
    onSubmit(entries)
  }

  function markAllPresent() {
    const all: Record<string, GXAttendanceStatus> = {}
    for (const booking of confirmedBookings) {
      all[booking.member.id] = 'present'
    }
    setAttendance(all)
  }

  const statuses: GXAttendanceStatus[] = ['present', 'absent', 'late']

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <button
          type="button"
          onClick={markAllPresent}
          className="text-sm text-blue-600 hover:text-blue-700"
        >
          {t('gx.attendance.markAllPresent')}
        </button>
      </div>

      <div className="divide-y">
        {confirmedBookings.map((booking) => {
          const memberName = isAr
            ? `${booking.member.firstNameAr} ${booking.member.lastNameAr}`
            : `${booking.member.firstNameEn} ${booking.member.lastNameEn}`
          const currentStatus = attendance[booking.member.id] ?? 'present'

          return (
            <div key={booking.member.id} className="flex items-center justify-between py-3">
              <span className="font-medium">{memberName}</span>
              <div className="flex gap-1">
                {statuses.map((status) => (
                  <button
                    key={status}
                    type="button"
                    onClick={() => handleStatusChange(booking.member.id, status)}
                    className={`rounded px-3 py-1 text-xs font-medium transition-colors ${
                      currentStatus === status
                        ? status === 'present'
                          ? 'bg-green-600 text-white'
                          : status === 'late'
                            ? 'bg-amber-600 text-white'
                            : 'bg-red-600 text-white'
                        : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                    }`}
                  >
                    {t(`gx.attendance.${status}`)}
                  </button>
                ))}
              </div>
            </div>
          )
        })}
      </div>

      <button
        type="button"
        onClick={handleSubmit}
        disabled={isSubmitting || confirmedBookings.length === 0}
        className="w-full rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700 disabled:opacity-50"
      >
        {isSubmitting ? t('common.submitting') : t('gx.attendance.submit')}
      </button>
    </div>
  )
}
