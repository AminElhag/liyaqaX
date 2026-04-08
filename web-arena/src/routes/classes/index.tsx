import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import {
  getSchedule,
  bookClass,
  cancelBooking,
  joinWaitlist,
  leaveWaitlist,
  acceptWaitlistOffer,
  classKeys,
} from '@/api/classes'
import type { GxScheduleItem } from '@/api/classes'
import { WaitlistBanner } from '@/components/gx/WaitlistBanner'
import { cn } from '@/lib/cn'

export const Route = createFileRoute('/classes/')({
  component: ClassesPage,
})

function ClassesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const { data: schedule, isLoading } = useQuery({
    queryKey: classKeys.schedule(),
    queryFn: getSchedule,
    staleTime: 120_000,
  })

  const [confirmingId, setConfirmingId] = useState<string | null>(null)

  const bookMutation = useMutation({
    mutationFn: bookClass,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: classKeys.all })
      setConfirmingId(null)
    },
  })

  const cancelMutation = useMutation({
    mutationFn: cancelBooking,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: classKeys.all })
    },
  })

  const joinWaitlistMutation = useMutation({
    mutationFn: joinWaitlist,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: classKeys.all })
    },
  })

  const leaveWaitlistMutation = useMutation({
    mutationFn: leaveWaitlist,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: classKeys.all })
    },
  })

  const acceptMutation = useMutation({
    mutationFn: acceptWaitlistOffer,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: classKeys.all })
    },
  })

  if (isLoading) {
    return <div className="p-4 text-center text-gray-500">{t('common.loading')}</div>
  }

  return (
    <div className="space-y-4 p-4">
      <h1 className="text-lg font-bold">{t('gx.schedule')}</h1>

      {(!schedule || schedule.length === 0) && (
        <p className="text-sm text-gray-500">{t('gx.waitlist.empty')}</p>
      )}

      {schedule?.map((item) => (
        <ClassCard
          key={item.id}
          item={item}
          confirmingId={confirmingId}
          onConfirmStart={() => setConfirmingId(item.id)}
          onConfirmCancel={() => setConfirmingId(null)}
          onBook={() => bookMutation.mutate(item.id)}
          onCancelBooking={() => cancelMutation.mutate(item.id)}
          onJoinWaitlist={() => joinWaitlistMutation.mutate(item.id)}
          onLeaveWaitlist={() => leaveWaitlistMutation.mutate(item.id)}
          onAcceptOffer={() => acceptMutation.mutate(item.id)}
          isBooking={bookMutation.isPending}
          isAccepting={acceptMutation.isPending}
        />
      ))}
    </div>
  )
}

interface ClassCardProps {
  item: GxScheduleItem
  confirmingId: string | null
  onConfirmStart: () => void
  onConfirmCancel: () => void
  onBook: () => void
  onCancelBooking: () => void
  onJoinWaitlist: () => void
  onLeaveWaitlist: () => void
  onAcceptOffer: () => void
  isBooking: boolean
  isAccepting: boolean
}

function ClassCard({
  item,
  confirmingId,
  onConfirmStart,
  onConfirmCancel,
  onBook,
  onCancelBooking,
  onJoinWaitlist,
  onLeaveWaitlist,
  onAcceptOffer,
  isBooking,
  isAccepting,
}: ClassCardProps) {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'
  const name = isAr ? item.classType.nameAr : item.classType.name
  const startTime = new Date(item.startTime).toLocaleTimeString(isAr ? 'ar-SA' : 'en-US', {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: 'Asia/Riyadh',
  })
  const startDate = new Date(item.startTime).toLocaleDateString(isAr ? 'ar-SA' : 'en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
    timeZone: 'Asia/Riyadh',
  })
  const isConfirming = confirmingId === item.id
  const isPast = new Date(item.startTime) < new Date()

  return (
    <div className="rounded-lg border bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between">
        <div>
          <div className="flex items-center gap-2">
            {item.classType.color && (
              <span
                className="inline-block h-3 w-3 rounded-full"
                style={{ backgroundColor: item.classType.color }}
              />
            )}
            <span className="text-sm font-semibold">{name}</span>
          </div>
          <p className="mt-1 text-xs text-gray-500">{item.instructorName}</p>
          <p className="text-xs text-gray-500">{startDate} {startTime}</p>
        </div>
        <div className="text-end text-xs text-gray-500">
          {item.spotsRemaining > 0
            ? t('gx.spotsLeft', { count: item.spotsRemaining })
            : t('gx.full')}
        </div>
      </div>

      {/* Waitlist offered banner */}
      {item.waitlistStatus === 'OFFERED' && item.waitlistOfferExpiresAt && (
        <div className="mt-3">
          <WaitlistBanner
            offerExpiresAt={item.waitlistOfferExpiresAt}
            onAccept={onAcceptOffer}
            isAccepting={isAccepting}
          />
        </div>
      )}

      {/* Waitlist position badge */}
      {item.waitlistStatus === 'WAITING' && item.waitlistPosition != null && (
        <div className="mt-3 rounded-md bg-gray-100 px-3 py-2 text-xs font-medium text-gray-700">
          {t('gx.waitlist.position', { position: item.waitlistPosition })}
        </div>
      )}

      {/* Action buttons */}
      <div className="mt-3">
        {isPast ? (
          <span className="text-xs text-gray-400">{t('gx.pastClass')}</span>
        ) : item.isBooked ? (
          /* Member has a booking — show cancel */
          isConfirming ? (
            <div className="flex gap-2">
              <button
                type="button"
                onClick={onCancelBooking}
                className="rounded-md bg-red-600 px-3 py-1.5 text-xs font-medium text-white"
              >
                {t('common.confirm')}
              </button>
              <button
                type="button"
                onClick={onConfirmCancel}
                className="rounded-md bg-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700"
              >
                {t('common.cancel')}
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={onConfirmStart}
              className="rounded-md bg-red-100 px-3 py-1.5 text-xs font-medium text-red-700"
            >
              {t('gx.cancelBooking')}
            </button>
          )
        ) : item.waitlistStatus === 'WAITING' ? (
          /* Member is waiting on waitlist */
          <button
            type="button"
            onClick={onLeaveWaitlist}
            className="rounded-md bg-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700"
          >
            {t('gx.waitlist.leave')}
          </button>
        ) : item.waitlistStatus === 'OFFERED' ? (
          /* Offered — banner handles the accept button */
          <button
            type="button"
            onClick={onLeaveWaitlist}
            className="rounded-md bg-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700"
          >
            {t('gx.waitlist.leave')}
          </button>
        ) : item.spotsRemaining > 0 ? (
          /* Spots available — book */
          isConfirming ? (
            <div className="flex gap-2">
              <button
                type="button"
                onClick={onBook}
                disabled={isBooking}
                className="rounded-md bg-green-600 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
              >
                {isBooking ? t('common.loading') : t('common.confirm')}
              </button>
              <button
                type="button"
                onClick={onConfirmCancel}
                className="rounded-md bg-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700"
              >
                {t('common.cancel')}
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={onConfirmStart}
              className={cn(
                'rounded-md px-3 py-1.5 text-xs font-medium text-white',
                item.spotsRemaining <= 3 ? 'bg-amber-600' : 'bg-green-600',
              )}
            >
              {item.spotsRemaining <= 3
                ? `${t('gx.book')} — ${t('gx.spotsLeft', { count: item.spotsRemaining })}`
                : t('gx.book')}
            </button>
          )
        ) : (
          /* Full — join waitlist */
          <button
            type="button"
            onClick={onJoinWaitlist}
            className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-white"
          >
            {t('gx.waitlist.join')}
          </button>
        )}
      </div>
    </div>
  )
}
