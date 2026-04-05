import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { getMemberGXBookings, gxKeys } from '@/api/gx'
import { GXStatusBadge } from '@/components/gx/GXStatusBadge'
import type { GXBooking } from '@/types/domain'

export const Route = createFileRoute('/members/$memberId/gx')({
  component: GxTab,
})

function GxTab() {
  const { memberId } = Route.useParams()
  const { t } = useTranslation()

  const { data, isLoading } = useQuery({
    queryKey: gxKeys.memberBookings(memberId),
    queryFn: () => getMemberGXBookings(memberId, { size: 20 }),
  })

  if (isLoading) {
    return <p className="text-gray-500">{t('common.loading')}</p>
  }

  const bookings = data?.items ?? []
  const now = new Date()
  const upcoming = bookings.filter(
    (b) => b.status !== 'cancelled' && new Date(b.bookedAt) >= now,
  )
  const past = bookings.filter(
    (b) => b.status === 'cancelled' || new Date(b.bookedAt) < now,
  )

  return (
    <div className="space-y-6">
      <div>
        <h3 className="mb-3 font-semibold">{t('gx.memberTab.upcoming')}</h3>
        {upcoming.length === 0 ? (
          <p className="text-sm text-gray-500">{t('gx.memberTab.noUpcoming')}</p>
        ) : (
          <div className="space-y-2">
            {upcoming.map((booking) => (
              <BookingRow key={booking.id} booking={booking} />
            ))}
          </div>
        )}
      </div>

      <div>
        <h3 className="mb-3 font-semibold">{t('gx.memberTab.history')}</h3>
        {past.length === 0 ? (
          <p className="text-sm text-gray-500">{t('gx.memberTab.noHistory')}</p>
        ) : (
          <div className="space-y-2">
            {past.map((booking) => (
              <BookingRow key={booking.id} booking={booking} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function BookingRow({ booking }: { booking: GXBooking }) {
  return (
    <div className="flex items-center justify-between rounded border p-3">
      <div>
        <p className="text-sm font-medium">
          {format(new Date(booking.bookedAt), 'dd/MM/yyyy HH:mm')}
        </p>
      </div>
      <GXStatusBadge status={booking.status} variant="booking" />
    </div>
  )
}
