import { createFileRoute } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getLapsedMembers,
  sendRenewalOffer,
  sendBulkRenewalOffers,
  lapsedKeys,
} from '@/api/memberLapse'
import type { LapsedMember } from '@/api/memberLapse'

export const Route = createFileRoute('/memberships/lapsed')({
  component: LapsedMembersPage,
})

function LapsedMembersPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [toastMsg, setToastMsg] = useState<string | null>(null)

  const showToast = (msg: string) => {
    setToastMsg(msg)
    setTimeout(() => setToastMsg(null), 4000)
  }

  const { data, isLoading } = useQuery({
    queryKey: lapsedKeys.list(page),
    queryFn: () => getLapsedMembers(page),
    staleTime: 120_000,
  })

  const offerMutation = useMutation({
    mutationFn: sendRenewalOffer,
    onSuccess: (result) => {
      showToast(result.message)
      queryClient.invalidateQueries({ queryKey: lapsedKeys.all })
    },
    onError: () => {
      showToast(t('lapsed.send_offer_error'))
    },
  })

  const bulkMutation = useMutation({
    mutationFn: sendBulkRenewalOffers,
    onSuccess: (result) => {
      showToast(t('lapsed.send_offer_bulk_success', { created: result.created, skipped: result.skipped }))
      setSelected(new Set())
      queryClient.invalidateQueries({ queryKey: lapsedKeys.all })
    },
    onError: () => {
      showToast(t('lapsed.send_offer_error'))
    },
  })

  const toggleSelect = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const toggleAll = () => {
    if (!data) return
    if (selected.size === data.members.length) {
      setSelected(new Set())
    } else {
      setSelected(new Set(data.members.map((m) => m.memberPublicId)))
    }
  }

  const handleBulkOffer = () => {
    if (selected.size === 0) return
    bulkMutation.mutate(Array.from(selected))
  }

  const totalPages = data ? Math.ceil(data.total / data.pageSize) : 0

  return (
    <div className="space-y-6">
      {toastMsg && (
        <div className="rounded-md bg-green-50 px-4 py-3 text-sm text-green-700">
          {toastMsg}
        </div>
      )}
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-gray-900">
          {t('lapsed.page_title')}
        </h2>
        {selected.size > 0 && (
          <button
            type="button"
            onClick={handleBulkOffer}
            disabled={bulkMutation.isPending}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {bulkMutation.isPending
              ? '...'
              : t('lapsed.send_offer_bulk', { count: selected.size })}
          </button>
        )}
      </div>

      {isLoading && (
        <div className="flex items-center justify-center py-12">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
        </div>
      )}

      {!isLoading && data && data.members.length === 0 && (
        <div className="rounded-lg border border-green-200 bg-green-50 p-8 text-center">
          <p className="text-sm text-green-700">{t('lapsed.empty')}</p>
        </div>
      )}

      {!isLoading && data && data.members.length > 0 && (
        <>
          <div className="overflow-hidden rounded-lg border border-gray-200">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-3 py-3">
                    <input
                      type="checkbox"
                      checked={selected.size === data.members.length}
                      onChange={toggleAll}
                      className="h-4 w-4 rounded border-gray-300"
                    />
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('members.columns.name')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('members.columns.phone')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('lapsed.columns.last_plan')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('lapsed.columns.expired_on')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('lapsed.columns.days_since')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase text-gray-500">
                    {t('lapsed.columns.follow_up_status')}
                  </th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 bg-white">
                {data.members.map((member: LapsedMember) => (
                  <tr key={member.memberPublicId} className="hover:bg-gray-50">
                    <td className="px-3 py-3">
                      <input
                        type="checkbox"
                        checked={selected.has(member.memberPublicId)}
                        onChange={() => toggleSelect(member.memberPublicId)}
                        className="h-4 w-4 rounded border-gray-300"
                      />
                    </td>
                    <td className="px-4 py-3 text-sm font-medium text-gray-900">
                      {member.nameEn}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {member.phone}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {member.lastMembershipPlan}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {member.expiredOn}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {member.daysSinceLapse}
                    </td>
                    <td className="px-4 py-3">
                      {member.hasOpenFollowUp ? (
                        <span className="text-green-600">&#10003;</span>
                      ) : (
                        <span className="text-gray-400">&#8212;</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <button
                        type="button"
                        onClick={() => offerMutation.mutate(member.memberPublicId)}
                        disabled={offerMutation.isPending}
                        className="text-sm font-medium text-blue-600 hover:text-blue-800 disabled:opacity-50"
                      >
                        {t('lapsed.send_offer')}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div className="flex items-center justify-between">
              <p className="text-sm text-gray-600">
                {t('lapsed.total', { count: data.total })}
              </p>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  disabled={page <= 1}
                  className="rounded border px-3 py-1 text-sm disabled:opacity-50"
                >
                  &laquo;
                </button>
                <span className="px-3 py-1 text-sm">
                  {page} / {totalPages}
                </span>
                <button
                  type="button"
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  disabled={page >= totalPages}
                  className="rounded border px-3 py-1 text-sm disabled:opacity-50"
                >
                  &raquo;
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}
