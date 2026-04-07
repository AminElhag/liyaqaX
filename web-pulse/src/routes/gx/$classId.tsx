import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { format } from 'date-fns'
import {
  getClassInstance,
  getClassBookings,
  cancelBooking,
  cancelClassInstance,
  gxKeys,
} from '@/api/gx'
import { GXStatusBadge } from '@/components/gx/GXStatusBadge'
import { GXCapacityBar } from '@/components/gx/GXCapacityBar'
import { GXBookingList } from '@/components/gx/GXBookingList'
import { WaitlistTab } from '@/components/gx/WaitlistTab'

export const Route = createFileRoute('/gx/$classId')({
  component: GXClassDetailPage,
})

function GXClassDetailPage() {
  const { classId } = Route.useParams()
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<'bookings' | 'waitlist'>('bookings')

  const { data: instance, isLoading } = useQuery({
    queryKey: gxKeys.instanceDetail(classId),
    queryFn: () => getClassInstance(classId),
  })

  const { data: bookingsData } = useQuery({
    queryKey: gxKeys.bookings(classId),
    queryFn: () => getClassBookings(classId, { size: 50 }),
    enabled: !!instance,
  })

  const cancelBookingMutation = useMutation({
    mutationFn: (bookingId: string) => cancelBooking(classId, bookingId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: gxKeys.instanceDetail(classId) })
      queryClient.invalidateQueries({ queryKey: gxKeys.bookings(classId) })
    },
  })

  const cancelClassMutation = useMutation({
    mutationFn: () => cancelClassInstance(classId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: gxKeys.instanceDetail(classId) })
      queryClient.invalidateQueries({ queryKey: gxKeys.bookings(classId) })
    },
  })

  if (isLoading || !instance) {
    return <p className="text-gray-500">{t('common.loading')}</p>
  }

  const className = isAr ? instance.classType.nameAr : instance.classType.nameEn
  const instructorName = isAr
    ? `${instance.instructor.firstNameAr} ${instance.instructor.lastNameAr}`
    : `${instance.instructor.firstNameEn} ${instance.instructor.lastNameEn}`
  const scheduledDate = new Date(instance.scheduledAt)
  const isPast = scheduledDate < new Date()

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-2xl font-bold">{className}</h1>
          <p className="text-gray-500">{instructorName}</p>
        </div>
        <GXStatusBadge status={instance.status} />
      </div>

      <div className="grid grid-cols-2 gap-4 rounded-lg border p-4 md:grid-cols-4">
        <div>
          <p className="text-sm text-gray-500">{t('gx.detail.date')}</p>
          <p className="font-medium">{format(scheduledDate, 'dd/MM/yyyy')}</p>
        </div>
        <div>
          <p className="text-sm text-gray-500">{t('gx.detail.time')}</p>
          <p className="font-medium">
            {format(scheduledDate, 'HH:mm')} &middot; {instance.durationMinutes}m
          </p>
        </div>
        <div>
          <p className="text-sm text-gray-500">{t('gx.detail.room')}</p>
          <p className="font-medium">{instance.room ?? '-'}</p>
        </div>
        <div>
          <p className="text-sm text-gray-500">{t('gx.detail.capacity')}</p>
          <GXCapacityBar
            bookingsCount={instance.bookingsCount}
            capacity={instance.capacity}
          />
        </div>
      </div>

      <div className="flex gap-2">
        {isPast && instance.status !== 'cancelled' && (
          <Link
            to="/gx/attendance"
            search={{ classId }}
            className="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700"
          >
            {t('gx.detail.takeAttendance')}
          </Link>
        )}
        {instance.status === 'scheduled' && (
          <button
            onClick={() => {
              if (window.confirm(t('gx.detail.cancelConfirm'))) {
                cancelClassMutation.mutate()
              }
            }}
            className="rounded border border-red-300 px-4 py-2 text-sm text-red-600 hover:bg-red-50"
          >
            {t('gx.detail.cancelClass')}
          </button>
        )}
      </div>

      <div>
        <div className="mb-4 flex gap-4 border-b">
          <button
            type="button"
            onClick={() => setActiveTab('bookings')}
            className={`pb-2 text-sm font-medium ${
              activeTab === 'bookings'
                ? 'border-b-2 border-blue-600 text-blue-600'
                : 'text-gray-500'
            }`}
          >
            {t('gx.detail.bookings')}
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('waitlist')}
            className={`pb-2 text-sm font-medium ${
              activeTab === 'waitlist'
                ? 'border-b-2 border-blue-600 text-blue-600'
                : 'text-gray-500'
            }`}
          >
            {t('gx.waitlist.tab_title')} ({instance.waitlistCount})
          </button>
        </div>

        {activeTab === 'bookings' ? (
          <GXBookingList
            bookings={bookingsData?.items ?? []}
            showCancelButton={instance.status !== 'cancelled'}
            onCancel={(bookingId) => cancelBookingMutation.mutate(bookingId)}
          />
        ) : (
          <WaitlistTab classId={classId} />
        )}
      </div>
    </div>
  )
}
