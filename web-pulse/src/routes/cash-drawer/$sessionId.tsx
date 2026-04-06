import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/useAuthStore'
import { Permission } from '@/types/permissions'
import { EntryList } from '@/components/cash-drawer/EntryList'
import { SessionSummaryCard } from '@/components/cash-drawer/SessionSummaryCard'
import { ReconcileModal } from '@/components/cash-drawer/ReconcileModal'
import { ShortSurplusBadge } from '@/components/cash-drawer/ShortSurplusBadge'
import {
  getSession,
  getEntries,
  reconcileSession,
  cashDrawerKeys,
} from '@/api/cashDrawer'
import type { ReconciliationStatus } from '@/types/domain'

export const Route = createFileRoute('/cash-drawer/$sessionId')({
  component: SessionDetailPage,
})

function SessionDetailPage() {
  const { t } = useTranslation()
  const { sessionId } = Route.useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const permissions = useAuthStore((s) => s.permissions)
  const [showReconcileModal, setShowReconcileModal] = useState(false)

  const { data: session, isLoading } = useQuery({
    queryKey: cashDrawerKeys.sessionDetail(sessionId),
    queryFn: () => getSession(sessionId),
  })

  const { data: entries = [] } = useQuery({
    queryKey: cashDrawerKeys.entries(sessionId),
    queryFn: () => getEntries(sessionId),
    enabled: !!session,
  })

  const reconcileMutation = useMutation({
    mutationFn: ({
      status,
      notes,
    }: {
      status: ReconciliationStatus
      notes?: string
    }) =>
      reconcileSession(sessionId, {
        reconciliationStatus: status,
        reconciliationNotes: notes,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cashDrawerKeys.sessionDetail(sessionId) })
      setShowReconcileModal(false)
    },
  })

  if (isLoading || !session) {
    return (
      <div className="p-6">
        <div className="h-48 animate-pulse rounded-lg bg-gray-100" />
      </div>
    )
  }

  const STATUS_STYLES: Record<string, string> = {
    open: 'bg-green-100 text-green-700',
    closed: 'bg-amber-100 text-amber-700',
    reconciled: 'bg-blue-100 text-blue-700',
  }

  return (
    <div className="space-y-4 p-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate({ to: '/cash-drawer/history' })}
            className="text-sm text-gray-500 hover:text-gray-700"
          >
            &larr; {t('cash_drawer.history')}
          </button>
          <h1 className="text-xl font-semibold text-gray-900">
            {t('cash_drawer.session_detail')}
          </h1>
          <span
            className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
              STATUS_STYLES[session.status] ?? 'bg-gray-100 text-gray-700'
            }`}
          >
            {session.status}
          </span>
        </div>
        {session.status === 'closed' &&
          permissions.has(Permission.CASH_DRAWER_RECONCILE) && (
            <button
              onClick={() => setShowReconcileModal(true)}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
            >
              {t('cash_drawer.reconcile.title')}
            </button>
          )}
      </div>

      {/* Session metadata */}
      <div className="grid grid-cols-2 gap-4 rounded-lg border border-gray-200 bg-white p-4 sm:grid-cols-4">
        <div>
          <p className="text-xs text-gray-500">{t('cash_drawer.branch')}</p>
          <p className="text-sm font-medium">{session.branch.name}</p>
        </div>
        <div>
          <p className="text-xs text-gray-500">{t('cash_drawer.opened_by')}</p>
          <p className="text-sm font-medium">
            {session.openedBy.firstName} {session.openedBy.lastName}
          </p>
        </div>
        <div>
          <p className="text-xs text-gray-500">{t('cash_drawer.opened_at')}</p>
          <p className="text-sm font-medium">
            {new Date(session.openedAt).toLocaleString()}
          </p>
        </div>
        <div>
          <p className="text-xs text-gray-500">{t('cash_drawer.closed_at')}</p>
          <p className="text-sm font-medium">
            {session.closedAt
              ? new Date(session.closedAt).toLocaleString()
              : '—'}
          </p>
        </div>
      </div>

      {/* Summary card for closed/reconciled sessions */}
      {session.status !== 'open' && <SessionSummaryCard session={session} />}

      {/* Reconciliation info */}
      {session.reconciliationStatus && (
        <div className="rounded-lg border border-gray-200 bg-white p-4">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium text-gray-700">
              {t('cash_drawer.reconcile.status')}:
            </span>
            <span
              className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                session.reconciliationStatus === 'approved'
                  ? 'bg-green-100 text-green-700'
                  : 'bg-red-100 text-red-700'
              }`}
            >
              {session.reconciliationStatus}
            </span>
            <ShortSurplusBadge difference={session.difference} />
          </div>
          {session.reconciliationNotes && (
            <p className="mt-2 text-sm text-gray-600">
              {session.reconciliationNotes}
            </p>
          )}
          {session.reconciledBy && (
            <p className="mt-1 text-xs text-gray-400">
              {t('cash_drawer.reconciled_by')}:{' '}
              {session.reconciledBy.firstName} {session.reconciledBy.lastName}
            </p>
          )}
        </div>
      )}

      <EntryList entries={entries} />

      <ReconcileModal
        isOpen={showReconcileModal}
        onClose={() => setShowReconcileModal(false)}
        onConfirm={(status, notes) =>
          reconcileMutation.mutate({ status, notes })
        }
        isLoading={reconcileMutation.isPending}
      />
    </div>
  )
}
