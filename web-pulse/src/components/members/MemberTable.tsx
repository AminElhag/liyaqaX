import { useTranslation } from 'react-i18next'
import type { MemberSummary } from '@/types/domain'
import type { PaginationMeta } from '@/types/api'
import { MemberRow } from './MemberRow'
import { EmptyState } from '@/components/shared/EmptyState'

interface MemberTableProps {
  members: MemberSummary[]
  pagination: PaginationMeta
  onPageChange: (page: number) => void
}

export function MemberTable({
  members,
  pagination,
  onPageChange,
}: MemberTableProps) {
  const { t } = useTranslation()

  if (members.length === 0) {
    return <EmptyState message={t('members.empty')} />
  }

  return (
    <div>
      <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white">
        <table className="w-full text-start">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-50">
              <th className="px-4 py-3 text-start text-xs font-medium tracking-wide text-gray-500 uppercase">
                {t('members.columns.name')}
              </th>
              <th className="px-4 py-3 text-start text-xs font-medium tracking-wide text-gray-500 uppercase">
                {t('members.columns.email')}
              </th>
              <th className="px-4 py-3 text-start text-xs font-medium tracking-wide text-gray-500 uppercase">
                {t('members.columns.phone')}
              </th>
              <th className="px-4 py-3 text-start text-xs font-medium tracking-wide text-gray-500 uppercase">
                {t('members.columns.status')}
              </th>
              <th className="px-4 py-3 text-start text-xs font-medium tracking-wide text-gray-500 uppercase">
                {t('members.columns.branch')}
              </th>
              <th className="px-4 py-3 text-start text-xs font-medium tracking-wide text-gray-500 uppercase">
                {t('members.columns.joinedAt')}
              </th>
            </tr>
          </thead>
          <tbody>
            {members.map((member) => (
              <MemberRow key={member.id} member={member} />
            ))}
          </tbody>
        </table>
      </div>

      {pagination.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between">
          <p className="text-sm text-gray-500">
            {t('common.page')} {pagination.page + 1} {t('common.of')}{' '}
            {pagination.totalPages}
          </p>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={pagination.page === 0}
              onClick={() => onPageChange(pagination.page - 1)}
              className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {t('common.previous')}
            </button>
            <button
              type="button"
              disabled={!pagination.hasNext}
              onClick={() => onPageChange(pagination.page + 1)}
              className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {t('common.next')}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
