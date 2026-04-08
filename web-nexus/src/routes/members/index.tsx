import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { searchMembers } from '@/api/members'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { EmptyState } from '@/components/common/EmptyState'
import { StatusBadge } from '@/components/common/StatusBadge'
import { Pagination } from '@/components/common/Pagination'

export const Route = createFileRoute('/members/')({
  component: MembersPage,
})

function MembersPage() {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [page, setPage] = useState(0)

  // Debounce search input by 300ms
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearch(search)
      setPage(0)
    }, 300)
    return () => clearTimeout(timer)
  }, [search])

  const isQueryEnabled = debouncedSearch.length >= 2

  const { data, isLoading } = useQuery({
    queryKey: ['members-search', debouncedSearch, page],
    queryFn: () => searchMembers(debouncedSearch, page),
    enabled: isQueryEnabled,
    staleTime: 60_000,
  })

  return (
    <div className="p-6">
      <h2 className="mb-6 text-xl font-semibold text-gray-900">
        {t('members.title')}
      </h2>

      <div className="mb-4">
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={t('members.search_placeholder')}
          className="w-full max-w-lg rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500 focus:outline-none"
        />
      </div>

      {!isQueryEnabled && search.length > 0 && (
        <p className="text-sm text-gray-500">{t('members.min_chars')}</p>
      )}

      {isQueryEnabled && isLoading && <LoadingSpinner />}

      {isQueryEnabled && data && data.items.length === 0 && <EmptyState />}

      {isQueryEnabled && data && data.items.length > 0 && (
        <>
          <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {isAr ? t('common.name_ar') : t('common.name_en')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('members.phone')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('members.email')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('members.club')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('members.organization')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('members.status')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {data.items.map((member) => (
                  <tr key={member.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <Link
                        to="/members/$memberId"
                        params={{ memberId: member.id }}
                        className="text-sm font-medium text-blue-600 hover:text-blue-800"
                      >
                        {isAr ? member.fullNameAr : member.fullNameEn}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {member.phone}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {member.email ?? '-'}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {isAr ? member.clubNameAr : member.clubNameEn}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {isAr ? member.organizationNameAr : member.organizationNameEn}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={member.membershipStatus} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <Pagination
            page={page}
            totalPages={data.pagination.totalPages}
            onPageChange={setPage}
          />
        </>
      )}
    </div>
  )
}
