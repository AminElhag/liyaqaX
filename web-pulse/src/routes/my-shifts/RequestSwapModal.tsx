import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { requestSwap, shiftKeys } from '@/api/shifts'
import { getStaffList, staffKeys } from '@/api/staff'

interface RequestSwapModalProps {
  shiftId: string
  onClose: () => void
}

export function RequestSwapModal({ shiftId, onClose }: RequestSwapModalProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const [targetStaffId, setTargetStaffId] = useState('')
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)

  const { data: staffData } = useQuery({
    queryKey: staffKeys.list({}),
    queryFn: () => getStaffList(),
  })

  const mutation = useMutation({
    mutationFn: () =>
      requestSwap(shiftId, {
        targetStaffPublicId: targetStaffId,
        requesterNote: note.trim() || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: shiftKeys.my() })
      onClose()
    },
    onError: (err: { detail?: string }) => {
      setError(err.detail ?? t('common.error', 'An error occurred'))
    },
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30">
      <div className="w-96 rounded-lg bg-white p-6 shadow-lg">
        <h2 className="mb-4 text-lg font-bold text-gray-900">{t('myshifts.swap_modal_title')}</h2>

        <div className="space-y-3">
          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">
              {t('myshifts.swap_modal_target')}
            </label>
            <select
              value={targetStaffId}
              onChange={(e) => setTargetStaffId(e.target.value)}
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

          <div>
            <label className="mb-1 block text-sm font-medium text-gray-700">
              {t('myshifts.swap_modal_note')}
            </label>
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              maxLength={300}
              rows={2}
              className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm"
            />
          </div>

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
            disabled={!targetStaffId || mutation.isPending}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {mutation.isPending ? t('common.saving', 'Saving...') : t('myshifts.request_swap')}
          </button>
        </div>
      </div>
    </div>
  )
}
