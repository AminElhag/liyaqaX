import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { getBranches, branchKeys } from '@/api/branches'
import { useAuthStore } from '@/stores/useAuthStore'
import type { Lead } from '@/types/domain'

interface ConvertLeadModalProps {
  lead: Lead
  isOpen: boolean
  onClose: () => void
  onConvert: (branchId: string) => void
  isLoading?: boolean
}

export function ConvertLeadModal({
  lead,
  isOpen,
  onClose,
  onConvert,
  isLoading,
}: ConvertLeadModalProps) {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'
  const [selectedBranchId, setSelectedBranchId] = useState('')
  const user = useAuthStore((s) => s.user)

  const { data: branchPage } = useQuery({
    queryKey: branchKeys.list(user?.organizationId ?? '', user?.clubId ?? ''),
    queryFn: () => getBranches(user!.organizationId!, user!.clubId!),
    enabled: isOpen && !!user?.organizationId && !!user?.clubId,
  })
  const branches = branchPage?.items

  if (!isOpen) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="w-full max-w-md rounded-lg bg-white p-6 shadow-xl">
        <h2 className="mb-4 text-lg font-semibold">
          {t('leads.convert.title')}
        </h2>

        <div className="mb-4 rounded-md bg-gray-50 p-3">
          <p className="text-sm font-medium">
            {lead.firstName} {lead.lastName}
          </p>
          {lead.phone && (
            <p className="text-sm text-gray-500" dir="ltr">
              {lead.phone}
            </p>
          )}
          {lead.email && (
            <p className="text-sm text-gray-500">{lead.email}</p>
          )}
        </div>

        <div className="mb-4">
          <label className="mb-1 block text-sm font-medium text-gray-700">
            {t('leads.convert.selectBranch')}
          </label>
          <select
            value={selectedBranchId}
            onChange={(e) => setSelectedBranchId(e.target.value)}
            className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
          >
            <option value="">{t('leads.convert.chooseBranch')}</option>
            {branches?.map((b) => (
              <option key={b.id} value={b.id}>
                {isAr ? b.nameAr : b.nameEn}
              </option>
            ))}
          </select>
        </div>

        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            {t('common.cancel')}
          </button>
          <button
            type="button"
            onClick={() => onConvert(selectedBranchId)}
            disabled={!selectedBranchId || isLoading}
            className="rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700 disabled:opacity-50"
          >
            {t('leads.convert.confirm')}
          </button>
        </div>
      </div>
    </div>
  )
}
