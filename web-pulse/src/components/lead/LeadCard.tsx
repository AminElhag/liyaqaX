import { LeadStageBadge } from './LeadStageBadge'
import { LeadSourceBadge } from './LeadSourceBadge'
import type { LeadSummary } from '@/types/domain'

interface LeadCardProps {
  lead: LeadSummary
  onClick?: () => void
}

export function LeadCard({ lead, onClick }: LeadCardProps) {
  return (
    <div
      className="cursor-pointer rounded-lg border border-gray-200 bg-white p-3 shadow-sm transition-shadow hover:shadow-md"
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') onClick?.()
      }}
    >
      <div className="mb-2 flex items-start justify-between">
        <p className="text-sm font-medium text-gray-900">
          {lead.firstName} {lead.lastName}
        </p>
        <LeadStageBadge stage={lead.stage} />
      </div>

      {lead.phone && (
        <p className="mb-1 text-xs text-gray-500" dir="ltr">
          {lead.phone}
        </p>
      )}

      <div className="flex items-center justify-between">
        <LeadSourceBadge source={lead.leadSource} />
        {lead.assignedStaff && (
          <span className="text-xs text-gray-400">
            {lead.assignedStaff.firstName}
          </span>
        )}
      </div>
    </div>
  )
}
