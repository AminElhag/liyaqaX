import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { getWaitlistEntries, removeWaitlistEntry, gxKeys } from '@/api/gx'
import type { WaitlistEntry } from '@/api/gx'

interface WaitlistTabProps {
  classId: string
}

export function WaitlistTab({ classId }: WaitlistTabProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [confirmingId, setConfirmingId] = useState<string | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: gxKeys.waitlistEntries(classId),
    queryFn: () => getWaitlistEntries(classId),
  })

  const removeMutation = useMutation({
    mutationFn: (entryId: string) => removeWaitlistEntry(classId, entryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: gxKeys.waitlistEntries(classId) })
      queryClient.invalidateQueries({ queryKey: gxKeys.instanceDetail(classId) })
      setConfirmingId(null)
    },
  })

  if (isLoading) {
    return <p className="py-4 text-center text-sm text-gray-500">{t('common.loading')}</p>
  }

  if (!data || data.entries.length === 0) {
    return (
      <p className="py-8 text-center text-sm text-gray-500">
        {t('gx.waitlist.empty_list')}
      </p>
    )
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
      <table className="w-full text-start">
        <thead className="border-b border-gray-200 bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wide text-gray-500">
              {t('gx.waitlist.col_position')}
            </th>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wide text-gray-500">
              {t('gx.waitlist.col_member')}
            </th>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wide text-gray-500">
              {t('gx.waitlist.col_phone')}
            </th>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wide text-gray-500">
              {t('gx.waitlist.col_status')}
            </th>
            <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wide text-gray-500">
              {t('gx.waitlist.col_expires')}
            </th>
            <th className="px-4 py-3" />
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {data.entries.map((entry: WaitlistEntry) => (
            <tr key={entry.entryId}>
              <td className="px-4 py-3 text-sm font-medium text-gray-900">
                #{entry.position}
              </td>
              <td className="px-4 py-3 text-sm text-gray-700">{entry.memberName}</td>
              <td className="px-4 py-3 text-sm text-gray-500">{entry.memberPhone}</td>
              <td className="px-4 py-3">
                <StatusBadge status={entry.status} />
              </td>
              <td className="px-4 py-3 text-sm text-gray-500">
                {entry.offerExpiresAt
                  ? new Date(entry.offerExpiresAt).toLocaleString('en-US', {
                      timeZone: 'Asia/Riyadh',
                      dateStyle: 'short',
                      timeStyle: 'short',
                    })
                  : '—'}
              </td>
              <td className="px-4 py-3">
                {confirmingId === entry.entryId ? (
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => removeMutation.mutate(entry.entryId)}
                      disabled={removeMutation.isPending}
                      className="rounded bg-red-600 px-2 py-1 text-xs font-medium text-white"
                    >
                      {t('common.confirm')}
                    </button>
                    <button
                      type="button"
                      onClick={() => setConfirmingId(null)}
                      className="rounded bg-gray-200 px-2 py-1 text-xs font-medium text-gray-700"
                    >
                      {t('common.cancel')}
                    </button>
                  </div>
                ) : (
                  <button
                    type="button"
                    onClick={() => setConfirmingId(entry.entryId)}
                    className="rounded bg-red-100 px-2 py-1 text-xs font-medium text-red-700 hover:bg-red-200"
                  >
                    {t('gx.waitlist.remove')}
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function StatusBadge({ status }: { status: string }) {
  const colors =
    status === 'OFFERED'
      ? 'bg-amber-100 text-amber-700'
      : status === 'WAITING'
        ? 'bg-blue-100 text-blue-700'
        : 'bg-gray-100 text-gray-700'

  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${colors}`}>
      {status}
    </span>
  )
}
