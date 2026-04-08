import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/stores/useAuthStore'
import { useBranchStore } from '@/stores/useBranchStore'
import { getBranches, branchKeys } from '@/api/branches'
import type { BranchSummary } from '@/types/domain'

export function BranchSelector() {
  const { t, i18n } = useTranslation()
  const user = useAuthStore((s) => s.user)
  const { activeBranch, setActiveBranch } = useBranchStore()

  const isAr = i18n.language === 'ar'
  const isOwner = user?.roleName?.toLowerCase().includes('owner')

  const { data } = useQuery({
    queryKey: branchKeys.list(user?.organizationId ?? '', user?.clubId ?? ''),
    queryFn: () => getBranches(user!.organizationId!, user!.clubId!),
    enabled: !!user?.organizationId && !!user?.clubId,
    staleTime: 5 * 60 * 1000,
  })

  const branches: BranchSummary[] = data?.items ?? []

  // Non-owner roles only see their assigned branches
  const visibleBranches = isOwner
    ? branches
    : branches.filter((b) => user?.branchIds.includes(b.id))

  const handleChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const value = e.target.value
    if (value === 'all') {
      setActiveBranch(null)
    } else {
      const branch = visibleBranches.find((b) => b.id === value)
      if (branch) setActiveBranch(branch)
    }
  }

  return (
    <div className="flex items-center gap-2">
      <label htmlFor="branch-selector" className="text-sm text-gray-500">
        {t('branch.selector')}
      </label>
      <select
        id="branch-selector"
        value={activeBranch?.id ?? 'all'}
        onChange={handleChange}
        className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500 focus:outline-none"
      >
        {isOwner && <option value="all">{t('branch.all')}</option>}
        {visibleBranches.map((branch) => (
          <option key={branch.id} value={branch.id}>
            {isAr ? branch.nameAr : branch.nameEn}
          </option>
        ))}
      </select>
    </div>
  )
}
