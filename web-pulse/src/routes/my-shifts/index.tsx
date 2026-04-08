import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { getMyShifts, shiftKeys } from '@/api/shifts'
import type { MyShiftItem } from '@/api/shifts'
import { RequestSwapModal } from './RequestSwapModal'
import { cn } from '@/lib/cn'

export const Route = createFileRoute('/my-shifts/')({
  component: MyShiftsPage,
})

function formatDateTime(isoStr: string): string {
  const d = new Date(isoStr)
  const date = d.toISOString().split('T')[0]
  const hours = d.getUTCHours().toString().padStart(2, '0')
  const mins = d.getUTCMinutes().toString().padStart(2, '0')
  return `${date} ${hours}:${mins}`
}

function formatHour(isoStr: string): string {
  const d = new Date(isoStr)
  return d.getUTCHours().toString().padStart(2, '0') + ':' + d.getUTCMinutes().toString().padStart(2, '0')
}

function SwapStatusBadge({ shift }: { shift: MyShiftItem }) {
  const { t } = useTranslation()
  const swap = shift.swapRequest
  if (!swap) return null

  const statusConfig: Record<string, { text: string; color: string }> = {
    PENDING_ACCEPTANCE: {
      text: t('myshifts.swap_status.pending_acceptance', { name: swap.targetStaffName }),
      color: 'bg-amber-100 text-amber-800',
    },
    PENDING_APPROVAL: {
      text: t('myshifts.swap_status.pending_approval'),
      color: 'bg-blue-100 text-blue-800',
    },
    APPROVED: {
      text: t('myshifts.swap_status.approved'),
      color: 'bg-green-100 text-green-800',
    },
    DECLINED: {
      text: t('myshifts.swap_status.declined'),
      color: 'bg-red-100 text-red-800',
    },
    CANCELLED: {
      text: t('myshifts.swap_status.cancelled'),
      color: 'bg-gray-100 text-gray-600',
    },
  }

  const config = statusConfig[swap.status]
  if (!config) return null

  return (
    <span className={cn('rounded-full px-2 py-0.5 text-xs font-medium', config.color)}>
      {config.text}
    </span>
  )
}

function MyShiftsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [swapShift, setSwapShift] = useState<MyShiftItem | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: shiftKeys.my(),
    queryFn: getMyShifts,
  })

  const shifts = data?.shifts ?? []

  return (
    <div className="p-6">
      <h1 className="mb-4 text-xl font-bold text-gray-900">{t('myshifts.page_title')}</h1>

      {isLoading ? (
        <div className="py-12 text-center text-gray-400">{t('common.loading', 'Loading...')}</div>
      ) : shifts.length === 0 ? (
        <div className="py-12 text-center text-gray-400">{t('myshifts.no_shifts')}</div>
      ) : (
        <div className="space-y-3">
          {shifts.map((shift) => (
            <div
              key={shift.shiftId}
              className="flex items-center justify-between rounded-lg border border-gray-200 p-4"
            >
              <div>
                <p className="font-medium text-gray-900">
                  {new Date(shift.startAt).toISOString().split('T')[0]}
                </p>
                <p className="text-sm text-gray-600">
                  {formatHour(shift.startAt)} – {formatHour(shift.endAt)} &middot; {shift.branchName}
                </p>
                {shift.notes && (
                  <p className="mt-1 text-sm text-gray-500">{shift.notes}</p>
                )}
                <div className="mt-1">
                  <SwapStatusBadge shift={shift} />
                </div>
              </div>
              <div>
                {!shift.swapRequest && (
                  <button
                    type="button"
                    onClick={() => setSwapShift(shift)}
                    className="rounded-md border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
                  >
                    {t('myshifts.request_swap')}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {swapShift && (
        <RequestSwapModal
          shiftId={swapShift.shiftId}
          onClose={() => setSwapShift(null)}
        />
      )}
    </div>
  )
}
