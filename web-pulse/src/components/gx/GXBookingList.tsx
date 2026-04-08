import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { GXStatusBadge } from './GXStatusBadge'
import type { GXBooking } from '@/types/domain'

interface GXBookingListProps {
  bookings: GXBooking[]
  onCancel?: (bookingId: string) => void
  showCancelButton?: boolean
}

export function GXBookingList({
  bookings,
  onCancel,
  showCancelButton = false,
}: GXBookingListProps) {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'

  if (bookings.length === 0) {
    return (
      <p className="py-4 text-center text-sm text-gray-500">
        {t('gx.bookings.empty')}
      </p>
    )
  }

  return (
    <div className="divide-y">
      {bookings.map((booking) => {
        const memberName = isAr
          ? `${booking.member.firstNameAr} ${booking.member.lastNameAr}`
          : `${booking.member.firstNameEn} ${booking.member.lastNameEn}`

        return (
          <div key={booking.id} className="flex items-center justify-between py-3">
            <div>
              <p className="font-medium">{memberName}</p>
              <div className="mt-1 flex items-center gap-2">
                <GXStatusBadge status={booking.status} variant="booking" />
                {booking.waitlistPosition != null && (
                  <span className="text-xs text-gray-500">
                    #{booking.waitlistPosition}
                  </span>
                )}
                <span className="text-xs text-gray-500">
                  {format(new Date(booking.bookedAt), 'dd/MM HH:mm')}
                </span>
              </div>
            </div>
            {showCancelButton &&
              booking.status !== 'cancelled' &&
              onCancel && (
                <button
                  onClick={() => onCancel(booking.id)}
                  className="text-sm text-red-600 hover:text-red-700"
                >
                  {t('gx.bookings.cancel')}
                </button>
              )}
          </div>
        )
      })}
    </div>
  )
}
