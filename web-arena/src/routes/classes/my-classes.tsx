import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { getBookings, getSchedule, acceptWaitlistOffer, leaveWaitlist, classKeys } from '@/api/classes'
import type { GxScheduleItem } from '@/api/classes'
import { WaitlistBanner } from '@/components/gx/WaitlistBanner'

export const Route = createFileRoute('/classes/my-classes')({
  component: ClassesMyClassesPage,
})

function ClassesMyClassesPage() {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<'bookings' | 'waitlist'>('bookings')

  return (
    <div className="space-y-4 p-4">
      <h1 className="text-lg font-bold">{t('gx.title')}</h1>

      <div className="flex gap-2 border-b">
        <button
          type="button"
          onClick={() => setActiveTab('bookings')}
          className={`pb-2 text-sm font-medium ${
            activeTab === 'bookings'
              ? 'border-b-2 border-blue-600 text-blue-600'
              : 'text-gray-500'
          }`}
        >
          {t('gx.booked')}
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
          {t('gx.waitlist.tab')}
        </button>
      </div>

      {activeTab === 'bookings' ? <BookingsTab /> : <WaitlistTab />}
    </div>
  )
}

function BookingsTab() {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'

  const { data: bookings, isLoading } = useQuery({
    queryKey: classKeys.bookings(),
    queryFn: getBookings,
  })

  if (isLoading) return <p className="text-sm text-gray-500">{t('common.loading')}</p>

  if (!bookings || bookings.length === 0) {
    return <p className="text-sm text-gray-500">{t('gx.waitlist.empty')}</p>
  }

  return (
    <div className="space-y-3">
      {bookings.map((b) => (
        <div key={b.id} className="rounded-lg border bg-white p-3">
          <p className="text-sm font-semibold">{isAr ? b.classNameAr : b.className}</p>
          <p className="text-xs text-gray-500">{b.instructorName}</p>
          <p className="text-xs text-gray-500">
            {new Date(b.startTime).toLocaleString(isAr ? 'ar-SA' : 'en-US', {
              timeZone: 'Asia/Riyadh',
              dateStyle: 'medium',
              timeStyle: 'short',
            })}
          </p>
          <span
            className={`mt-1 inline-block rounded-full px-2 py-0.5 text-xs font-medium ${
              b.status === 'confirmed'
                ? 'bg-green-100 text-green-700'
                : b.status === 'cancelled'
                  ? 'bg-red-100 text-red-700'
                  : 'bg-gray-100 text-gray-700'
            }`}
          >
            {b.status}
          </span>
        </div>
      ))}
    </div>
  )
}

function WaitlistTab() {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'
  const queryClient = useQueryClient()

  const { data: schedule, isLoading } = useQuery({
    queryKey: classKeys.schedule(),
    queryFn: getSchedule,
  })

  const acceptMutation = useMutation({
    mutationFn: acceptWaitlistOffer,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: classKeys.all })
    },
  })

  const leaveMutation = useMutation({
    mutationFn: leaveWaitlist,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: classKeys.all })
    },
  })

  if (isLoading) return <p className="text-sm text-gray-500">{t('common.loading')}</p>

  const waitlistItems = schedule?.filter(
    (item: GxScheduleItem) =>
      item.waitlistStatus === 'WAITING' || item.waitlistStatus === 'OFFERED',
  ) ?? []

  if (waitlistItems.length === 0) {
    return <p className="text-sm text-gray-500">{t('gx.waitlist.empty')}</p>
  }

  return (
    <div className="space-y-3">
      {waitlistItems.map((item: GxScheduleItem) => (
        <div key={item.id} className="rounded-lg border bg-white p-3">
          <p className="text-sm font-semibold">
            {isAr ? item.classType.nameAr : item.classType.name}
          </p>
          <p className="text-xs text-gray-500">
            {new Date(item.startTime).toLocaleString(isAr ? 'ar-SA' : 'en-US', {
              timeZone: 'Asia/Riyadh',
              dateStyle: 'medium',
              timeStyle: 'short',
            })}
          </p>

          {item.waitlistStatus === 'OFFERED' && item.waitlistOfferExpiresAt && (
            <div className="mt-2">
              <WaitlistBanner
                offerExpiresAt={item.waitlistOfferExpiresAt}
                onAccept={() => acceptMutation.mutate(item.id)}
                isAccepting={acceptMutation.isPending}
              />
            </div>
          )}

          {item.waitlistStatus === 'WAITING' && item.waitlistPosition != null && (
            <p className="mt-2 text-xs font-medium text-gray-600">
              {t('gx.waitlist.position', { position: item.waitlistPosition })}
            </p>
          )}

          <button
            type="button"
            onClick={() => leaveMutation.mutate(item.id)}
            className="mt-2 rounded-md bg-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700"
          >
            {t('gx.waitlist.leave')}
          </button>
        </div>
      ))}
    </div>
  )
}
