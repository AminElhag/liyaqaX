import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createShift, shiftKeys } from '@/api/shifts'
import { getStaffList, staffKeys } from '@/api/staff'

interface AddShiftModalProps {
  branchId: string
  weekStart: string
  defaultDay: number | null
  onClose: () => void
}

export function AddShiftModal({ branchId, weekStart, defaultDay, onClose }: AddShiftModalProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const [staffMemberId, setStaffMemberId] = useState('')
  const [date, setDate] = useState(() => {
    if (defaultDay != null) {
      const d = new Date(weekStart + 'T00:00:00Z')
      d.setUTCDate(d.getUTCDate() + defaultDay)
      return d.toISOString().split('T')[0]
    }
    return weekStart
  })
  const [startTime, setStartTime] = useState('06:00')
  const [endTime, setEndTime] = useState('14:00')
  const [notes, setNotes] = useState('')
  const [error, setError] = useState<string | null>(null)

  const { data: staffData } = useQuery({
    queryKey: staffKeys.list({}),
    queryFn: () => getStaffList(),
  })

  const mutation = useMutation({
    mutationFn: () =>
      createShift({
        staffMemberPublicId: staffMemberId,
        branchPublicId: branchId,
        startAt: `${date}T${startTime}:00Z`,
        endAt: `${date}T${endTime}:00Z`,
        notes: notes.trim() || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: shiftKeys.all })
      onClose()
    },
    onError: (err: { detail?: string; errorCode?: string }) => {
      setError(err.detail ?? t('common.error', 'An error occurred'))
    },
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
      <div className="w-96 rounded-lg bg-white p-6 shadow-lg">
        <h2 className="mb-4 text-lg font-bold text-gray-900">{t('schedule.shift_modal_title')}</h2>

        <div className="space-y-3">
          {/* Staff picker */}
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">
              {t('schedule.shift_modal_staff')}
            </label>
            <select
              value={staffMemberId}
              onChange={(e) => setStaffMemberId(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
            >
              <option value="">{t('common.select', 'Select...')}</option>
              {staffData?.items.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.firstNameEn} {s.lastNameEn}
                </option>
              ))}
            </select>
          </div>

          {/* Date */}
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">
              {t('schedule.shift_modal_date')}
            </label>
            <input
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
          </div>

          {/* Start time */}
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">
              {t('schedule.shift_modal_start')}
            </label>
            <input
              type="time"
              value={startTime}
              onChange={(e) => setStartTime(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
          </div>

          {/* End time */}
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">
              {t('schedule.shift_modal_end')}
            </label>
            <input
              type="time"
              value={endTime}
              onChange={(e) => setEndTime(e.target.value)}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
          </div>

          {/* Notes */}
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">
              {t('schedule.shift_modal_notes')}
            </label>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              maxLength={500}
              rows={2}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
          </div>

          {/* Inline error (e.g., 409 SHIFT_OVERLAP) */}
          {error && (
            <p className="rounded-md bg-red-50 p-2 text-sm text-red-700">{error}</p>
          )}
        </div>

        <div className="mt-4 flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-md px-4 py-2 text-sm text-gray-600 hover:bg-gray-100"
          >
            {t('common.cancel', 'Cancel')}
          </button>
          <button
            type="button"
            onClick={() => { setError(null); mutation.mutate() }}
            disabled={!staffMemberId || mutation.isPending}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {mutation.isPending ? t('common.saving', 'Saving...') : t('common.save', 'Save')}
          </button>
        </div>
      </div>
    </div>
  )
}
