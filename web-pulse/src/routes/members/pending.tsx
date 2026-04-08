import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getPendingMembers,
  activateMember,
  rejectMember,
  memberKeys,
} from '@/api/members'
import type { PendingMember } from '@/api/members'

export const Route = createFileRoute('/members/pending')({
  component: PendingMembersPage,
})

function PendingMembersPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [activateTarget, setActivateTarget] = useState<PendingMember | null>(null)
  const [rejectTarget, setRejectTarget] = useState<PendingMember | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const [planId, setPlanId] = useState<string | undefined>(undefined)

  const { data, isLoading } = useQuery({
    queryKey: memberKeys.pending({ page, size: 20 }),
    queryFn: () => getPendingMembers({ page, size: 20 }),
  })

  const activateMut = useMutation({
    mutationFn: ({ id, req }: { id: string; req: { membershipPlanId?: string } }) =>
      activateMember(id, req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: memberKeys.all })
      setActivateTarget(null)
      setPlanId(undefined)
    },
  })

  const rejectMut = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      rejectMember(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: memberKeys.all })
      setRejectTarget(null)
      setRejectReason('')
    },
  })

  const thClass = 'px-4 py-3 text-start text-sm font-semibold text-gray-700'
  const tdClass = 'px-4 py-3 text-sm'

  return (
    <div className="space-y-6 p-6">
      <h1 className="text-2xl font-bold">{t('members.pendingTitle')}</h1>

      {isLoading ? (
        <p className="text-gray-500">{t('common.loading')}</p>
      ) : !data?.items.length ? (
        <p className="text-gray-500">{t('members.noPending')}</p>
      ) : (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className={thClass}>{t('members.name')}</th>
                <th className={thClass}>{t('members.phone')}</th>
                <th className={thClass}>{t('members.desiredPlan')}</th>
                <th className={thClass}>{t('members.registeredAt')}</th>
                <th className={thClass}>{t('members.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {data.items.map((member) => (
                <tr key={member.id}>
                  <td className={tdClass}>
                    <div>{member.nameAr}</div>
                    <div className="text-xs text-gray-400">{member.nameEn}</div>
                  </td>
                  <td className={`${tdClass}`} dir="ltr">{member.phone}</td>
                  <td className={tdClass}>
                    {member.intent?.planNameAr || member.intent?.planNameEn || '—'}
                    {member.intent?.planPriceSar && (
                      <span className="ms-1 text-xs text-gray-400">
                        ({member.intent.planPriceSar} SAR)
                      </span>
                    )}
                  </td>
                  <td className={tdClass}>
                    {new Date(member.registeredAt).toLocaleDateString()}
                  </td>
                  <td className={`${tdClass} space-x-2`}>
                    <button
                      onClick={() => {
                        setActivateTarget(member)
                        setPlanId(member.intent?.planId || undefined)
                      }}
                      className="rounded bg-green-600 px-3 py-1 text-xs font-semibold text-white hover:bg-green-700"
                    >
                      {t('members.activate')}
                    </button>
                    <button
                      onClick={() => setRejectTarget(member)}
                      className="rounded bg-red-600 px-3 py-1 text-xs font-semibold text-white hover:bg-red-700"
                    >
                      {t('members.reject')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && data.pagination.totalPages > 1 && (
        <div className="flex gap-2">
          <button
            disabled={page === 0}
            onClick={() => setPage(p => p - 1)}
            className="rounded border px-3 py-1 text-sm disabled:opacity-50"
          >
            {t('common.previous')}
          </button>
          <span className="px-2 py-1 text-sm">
            {page + 1} / {data.pagination.totalPages}
          </span>
          <button
            disabled={!data.pagination.hasNext}
            onClick={() => setPage(p => p + 1)}
            className="rounded border px-3 py-1 text-sm disabled:opacity-50"
          >
            {t('common.next')}
          </button>
        </div>
      )}

      {/* Activate Modal */}
      {activateTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
            <h2 className="mb-4 text-lg font-bold">{t('members.activateTitle')}</h2>
            <p className="mb-4">{activateTarget.nameAr} ({activateTarget.nameEn})</p>
            {activateTarget.intent?.planNameAr && (
              <p className="mb-4 text-sm text-gray-500">
                {t('members.desiredPlan')}: {activateTarget.intent.planNameAr}
              </p>
            )}
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setActivateTarget(null)}
                className="rounded border px-4 py-2 text-sm"
              >
                {t('common.cancel')}
              </button>
              <button
                onClick={() => activateMut.mutate({
                  id: activateTarget.id,
                  req: { membershipPlanId: planId },
                })}
                disabled={activateMut.isPending}
                className="rounded bg-green-600 px-4 py-2 text-sm font-semibold text-white hover:bg-green-700 disabled:opacity-50"
              >
                {activateMut.isPending ? t('common.loading') : t('members.confirmActivate')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Reject Modal */}
      {rejectTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
            <h2 className="mb-4 text-lg font-bold">{t('members.rejectTitle')}</h2>
            <p className="mb-2">{rejectTarget.nameAr} ({rejectTarget.nameEn})</p>
            <textarea
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder={t('members.rejectReasonPlaceholder')}
              className="mb-4 w-full rounded-lg border px-4 py-3 text-sm"
              rows={3}
            />
            <div className="flex justify-end gap-3">
              <button
                onClick={() => { setRejectTarget(null); setRejectReason('') }}
                className="rounded border px-4 py-2 text-sm"
              >
                {t('common.cancel')}
              </button>
              <button
                onClick={() => rejectMut.mutate({
                  id: rejectTarget.id,
                  reason: rejectReason,
                })}
                disabled={rejectMut.isPending || rejectReason.length < 10}
                className="rounded bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-50"
              >
                {rejectMut.isPending ? t('common.loading') : t('members.confirmReject')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
