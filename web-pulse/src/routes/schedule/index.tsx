import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { useBranchStore } from '@/stores/useBranchStore'
import {
  getRoster,
  getPendingSwaps,
  resolveSwap,
  deleteShift,
  shiftKeys,
} from '@/api/shifts'
import type { RosterShiftItem } from '@/api/shifts'
import { AddShiftModal } from './AddShiftModal'
import { cn } from '@/lib/cn'

export const Route = createFileRoute('/schedule/')({
  component: SchedulePage,
})

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const

function getMonday(date: Date): string {
  const d = new Date(date)
  const day = d.getDay()
  const diff = d.getDate() - day + (day === 0 ? -6 : 1)
  d.setDate(diff)
  return d.toISOString().split('T')[0]
}

function addDays(dateStr: string, days: number): string {
  const d = new Date(dateStr + 'T00:00:00Z')
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().split('T')[0]
}

function formatHour(isoStr: string): string {
  const d = new Date(isoStr)
  return d.getUTCHours().toString().padStart(2, '0') + ':' + d.getUTCMinutes().toString().padStart(2, '0')
}

function getDayIndex(isoStr: string, weekStart: string): number {
  const shiftDate = new Date(isoStr).toISOString().split('T')[0]
  const startMs = new Date(weekStart + 'T00:00:00Z').getTime()
  const shiftMs = new Date(shiftDate + 'T00:00:00Z').getTime()
  return Math.floor((shiftMs - startMs) / (86400000))
}

function SchedulePage() {
  const { t } = useTranslation()
  const activeBranch = useBranchStore((s) => s.activeBranch)
  const queryClient = useQueryClient()

  const [weekStart, setWeekStart] = useState(() => getMonday(new Date()))
  const [showAddModal, setShowAddModal] = useState(false)
  const [selectedDay, setSelectedDay] = useState<number | null>(null)
  const [selectedShift, setSelectedShift] = useState<RosterShiftItem | null>(null)

  const branchId = activeBranch?.id ?? ''

  const { data: roster, isLoading } = useQuery({
    queryKey: shiftKeys.roster(branchId, weekStart),
    queryFn: () => getRoster(branchId, weekStart),
    enabled: !!branchId,
  })

  const { data: pendingData } = useQuery({
    queryKey: shiftKeys.pendingSwaps(),
    queryFn: getPendingSwaps,
    enabled: !!branchId,
  })

  const resolveMutation = useMutation({
    mutationFn: ({ swapId, action }: { swapId: string; action: 'approve' | 'reject' }) =>
      resolveSwap(swapId, action),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: shiftKeys.all })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (shiftId: string) => deleteShift(shiftId),
    onSuccess: () => {
      setSelectedShift(null)
      queryClient.invalidateQueries({ queryKey: shiftKeys.all })
    },
  })

  const prevWeek = () => setWeekStart((w) => addDays(w, -7))
  const nextWeek = () => setWeekStart((w) => addDays(w, 7))

  // Group shifts by staff member
  const staffMap = new Map<string, { name: string; shifts: Map<number, RosterShiftItem[]> }>()
  roster?.shifts.forEach((shift) => {
    if (!staffMap.has(shift.staffMemberId)) {
      staffMap.set(shift.staffMemberId, { name: shift.staffMemberName, shifts: new Map() })
    }
    const dayIdx = getDayIndex(shift.startAt, weekStart)
    const entry = staffMap.get(shift.staffMemberId)!
    if (!entry.shifts.has(dayIdx)) entry.shifts.set(dayIdx, [])
    entry.shifts.get(dayIdx)!.push(shift)
  })

  if (!branchId) {
    return (
      <div className="p-6 text-gray-500">{t('schedule.select_branch', 'Select a branch to view the schedule.')}</div>
    )
  }

  return (
    <div className="p-6">
      {/* Header */}
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900">{t('schedule.page_title')}</h1>
        <div className="flex items-center gap-3">
          <button type="button" onClick={prevWeek} className="rounded border px-3 py-1 text-sm hover:bg-gray-50">
            &larr;
          </button>
          <span className="text-sm font-medium text-gray-700">
            {t('schedule.week_of')} {weekStart}
          </span>
          <button type="button" onClick={nextWeek} className="rounded border px-3 py-1 text-sm hover:bg-gray-50">
            &rarr;
          </button>
        </div>
        <button
          type="button"
          onClick={() => { setSelectedDay(null); setShowAddModal(true) }}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
        >
          {t('schedule.add_shift')}
        </button>
      </div>

      {/* Roster Grid */}
      {isLoading ? (
        <div className="py-12 text-center text-gray-400">{t('common.loading', 'Loading...')}</div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-gray-200">
          <table className="w-full table-fixed text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="w-36 border-e px-3 py-2 text-start font-medium text-gray-700">
                  {t('schedule.staff_col', 'Staff')}
                </th>
                {DAYS.map((d, i) => (
                  <th key={d} className="border-e px-2 py-2 text-center font-medium text-gray-700">
                    {d} <span className="text-xs text-gray-400">{addDays(weekStart, i).slice(5)}</span>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {staffMap.size === 0 ? (
                <tr>
                  <td colSpan={8} className="py-8 text-center text-gray-400">
                    {t('schedule.empty', 'No shifts scheduled for this week.')}
                  </td>
                </tr>
              ) : (
                Array.from(staffMap.entries()).map(([staffId, { name, shifts }]) => (
                  <tr key={staffId} className="border-t">
                    <td className="border-e px-3 py-2 font-medium text-gray-900">{name}</td>
                    {DAYS.map((_, dayIdx) => {
                      const dayShifts = shifts.get(dayIdx) ?? []
                      return (
                        <td key={dayIdx} className="border-e px-1 py-1 text-center">
                          {dayShifts.map((s) => (
                            <button
                              key={s.shiftId}
                              type="button"
                              onClick={() => setSelectedShift(s)}
                              className={cn(
                                'mb-0.5 inline-block rounded px-1.5 py-0.5 text-xs font-medium',
                                s.hasPendingSwap
                                  ? 'bg-amber-100 text-amber-800'
                                  : 'bg-blue-100 text-blue-800',
                              )}
                            >
                              {formatHour(s.startAt)}–{formatHour(s.endAt)}
                              {s.hasPendingSwap && ' ⇆'}
                            </button>
                          ))}
                        </td>
                      )
                    })}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Shift detail popover */}
      {selectedShift && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" onClick={() => setSelectedShift(null)}>
          <div className="w-80 rounded-lg bg-white p-4 shadow-lg" onClick={(e) => e.stopPropagation()}>
            <h3 className="mb-2 font-bold text-gray-900">{selectedShift.staffMemberName}</h3>
            <p className="text-sm text-gray-600">
              {formatHour(selectedShift.startAt)} – {formatHour(selectedShift.endAt)}
            </p>
            {selectedShift.notes && (
              <p className="mt-1 text-sm text-gray-500">{selectedShift.notes}</p>
            )}
            {selectedShift.hasPendingSwap && (
              <span className="mt-2 inline-block rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800">
                {t('schedule.pending_swap_badge', 'Pending Swap')}
              </span>
            )}
            <div className="mt-3 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setSelectedShift(null)}
                className="rounded px-3 py-1 text-sm text-gray-600 hover:bg-gray-100"
              >
                {t('common.close', 'Close')}
              </button>
              <button
                type="button"
                onClick={() => {
                  if (window.confirm(t('schedule.delete_shift_confirm'))) {
                    deleteMutation.mutate(selectedShift.shiftId)
                  }
                }}
                className="rounded bg-red-600 px-3 py-1 text-sm text-white hover:bg-red-700"
                disabled={deleteMutation.isPending}
              >
                {t('common.delete', 'Delete')}
              </button>
            </div>
            {deleteMutation.isError && (
              <p className="mt-2 text-sm text-red-600">
                {(deleteMutation.error as { detail?: string })?.detail ?? t('common.error', 'An error occurred')}
              </p>
            )}
          </div>
        </div>
      )}

      {/* Pending Swap Requests Panel */}
      {pendingData && pendingData.swapRequests.length > 0 && (
        <div className="mt-6">
          <h2 className="mb-3 text-lg font-bold text-gray-900">{t('schedule.pending_swaps_title')}</h2>
          <div className="space-y-3">
            {pendingData.swapRequests.map((swap) => (
              <div key={swap.swapId} className="rounded-lg border border-gray-200 p-4">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-medium text-gray-900">
                      {swap.requesterName} &rarr; {swap.targetName}
                    </p>
                    <p className="text-sm text-gray-500">
                      {swap.shiftDate} &middot; {formatHour(swap.shiftStart)}–{formatHour(swap.shiftEnd)}
                    </p>
                    {swap.requesterNote && (
                      <p className="mt-1 text-sm italic text-gray-500">"{swap.requesterNote}"</p>
                    )}
                  </div>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => resolveMutation.mutate({ swapId: swap.swapId, action: 'approve' })}
                      className="rounded bg-green-600 px-3 py-1 text-sm font-medium text-white hover:bg-green-700"
                      disabled={resolveMutation.isPending}
                    >
                      {t('schedule.swap_approve')}
                    </button>
                    <button
                      type="button"
                      onClick={() => resolveMutation.mutate({ swapId: swap.swapId, action: 'reject' })}
                      className="rounded bg-red-600 px-3 py-1 text-sm font-medium text-white hover:bg-red-700"
                      disabled={resolveMutation.isPending}
                    >
                      {t('schedule.swap_reject')}
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Add Shift Modal */}
      {showAddModal && (
        <AddShiftModal
          branchId={branchId}
          weekStart={weekStart}
          defaultDay={selectedDay}
          onClose={() => setShowAddModal(false)}
        />
      )}
    </div>
  )
}
