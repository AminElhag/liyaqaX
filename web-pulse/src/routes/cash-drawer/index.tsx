import { createFileRoute } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useBranchStore } from '@/stores/useBranchStore'
import { useAuthStore } from '@/stores/useAuthStore'
import { PermissionGate } from '@/components/shared/PermissionGate'
import { Permission } from '@/types/permissions'
import { DrawerStatusCard } from '@/components/cash-drawer/DrawerStatusCard'
import { EntryForm } from '@/components/cash-drawer/EntryForm'
import { EntryList } from '@/components/cash-drawer/EntryList'
import { CloseSessionModal } from '@/components/cash-drawer/CloseSessionModal'
import {
  getCurrentSession,
  openSession,
  addEntry,
  closeSession,
  getEntries,
  cashDrawerKeys,
} from '@/api/cashDrawer'
import type { CreateEntryRequest } from '@/types/domain'

export const Route = createFileRoute('/cash-drawer/')({
  component: CashDrawerPage,
})

function CashDrawerPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const activeBranch = useBranchStore((s) => s.activeBranch)
  const permissions = useAuthStore((s) => s.permissions)
  const [showCloseModal, setShowCloseModal] = useState(false)
  const [openingAmount, setOpeningAmount] = useState('')

  const branchId = activeBranch?.id ?? ''

  const { data: session, isLoading } = useQuery({
    queryKey: cashDrawerKeys.currentSession(branchId),
    queryFn: () => getCurrentSession(branchId),
    enabled: !!branchId,
    staleTime: 0,
  })

  const { data: entries = [] } = useQuery({
    queryKey: cashDrawerKeys.entries(session?.id ?? ''),
    queryFn: () => getEntries(session!.id),
    enabled: !!session?.id && session.status === 'open',
    staleTime: 0,
  })

  const openMutation = useMutation({
    mutationFn: (openingFloatHalalas: number) =>
      openSession(branchId, { openingFloatHalalas }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cashDrawerKeys.currentSession(branchId) })
      setOpeningAmount('')
    },
  })

  const addEntryMutation = useMutation({
    mutationFn: (request: CreateEntryRequest) =>
      addEntry(session!.id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cashDrawerKeys.currentSession(branchId) })
      queryClient.invalidateQueries({ queryKey: cashDrawerKeys.entries(session!.id) })
    },
  })

  const closeMutation = useMutation({
    mutationFn: (countedClosingHalalas: number) =>
      closeSession(session!.id, { countedClosingHalalas }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: cashDrawerKeys.currentSession(branchId) })
      setShowCloseModal(false)
    },
  })

  if (!branchId) {
    return (
      <div className="p-6">
        <p className="text-sm text-gray-500">{t('cash_drawer.select_branch')}</p>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="p-6">
        <div className="h-32 animate-pulse rounded-lg bg-gray-100" />
      </div>
    )
  }

  const handleOpen = () => {
    const sar = parseFloat(openingAmount)
    if (isNaN(sar) || sar < 0) return
    openMutation.mutate(Math.round(sar * 100))
  }

  // No open session
  if (!session || session.status !== 'open') {
    return (
      <div className="space-y-4 p-6">
        <h1 className="text-xl font-semibold text-gray-900">
          {t('cash_drawer.title')}
        </h1>
        <div className="rounded-lg border border-gray-200 bg-white p-6 text-center">
          <p className="mb-4 text-sm text-gray-500">
            {t('cash_drawer.no_open_session')}
          </p>
          <PermissionGate permission={Permission.CASH_DRAWER_OPEN}>
            <div className="mx-auto flex max-w-xs items-center gap-2">
              <input
                type="number"
                step="0.01"
                min="0"
                placeholder={t('cash_drawer.opening_float') + ' (SAR)'}
                value={openingAmount}
                onChange={(e) => setOpeningAmount(e.target.value)}
                className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
              />
              <button
                onClick={handleOpen}
                disabled={!openingAmount || openMutation.isPending}
                className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
              >
                {t('cash_drawer.open_session')}
              </button>
            </div>
          </PermissionGate>
        </div>
      </div>
    )
  }

  // Open session
  return (
    <div className="space-y-4 p-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-gray-900">
          {t('cash_drawer.title')}
        </h1>
        <PermissionGate permission={Permission.CASH_DRAWER_CLOSE}>
          <button
            onClick={() => setShowCloseModal(true)}
            className="rounded-md bg-amber-600 px-4 py-2 text-sm font-medium text-white hover:bg-amber-700"
          >
            {t('cash_drawer.close_session')}
          </button>
        </PermissionGate>
      </div>

      <DrawerStatusCard session={session} />

      {permissions.has(Permission.CASH_DRAWER_ENTRY_CREATE) && (
        <EntryForm
          onSubmit={(data) => addEntryMutation.mutate(data)}
          isSubmitting={addEntryMutation.isPending}
        />
      )}

      <EntryList entries={entries} />

      <CloseSessionModal
        isOpen={showCloseModal}
        onClose={() => setShowCloseModal(false)}
        onConfirm={(amount) => closeMutation.mutate(amount)}
        isLoading={closeMutation.isPending}
      />
    </div>
  )
}
