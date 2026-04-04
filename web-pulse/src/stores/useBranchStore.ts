import { create } from 'zustand'
import type { BranchSummary } from '@/types/domain'

interface BranchState {
  /** Currently selected branch, or null for "All branches" (club:owner only) */
  activeBranch: BranchSummary | null
  setActiveBranch: (branch: BranchSummary | null) => void
}

export const useBranchStore = create<BranchState>()((set) => ({
  activeBranch: null,

  setActiveBranch: (branch) => set({ activeBranch: branch }),
}))
