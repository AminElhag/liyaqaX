import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { listOrgs } from '@/api/organizations'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { EmptyState } from '@/components/common/EmptyState'
import { Pagination } from '@/components/common/Pagination'
import { PermissionGate } from '@/components/common/PermissionGate'
import { Permission } from '@/types/permissions'

export const Route = createFileRoute('/organizations/')({
  component: OrganizationsPage,
})

function OrganizationsPage() {
  const { t, i18n } = useTranslation()
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['organizations', search, page],
    queryFn: () => listOrgs(search || undefined, page),
    staleTime: 120_000,
  })

  const isAr = i18n.language === 'ar'

  return (
    <div className="p-6">
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-xl font-semibold text-gray-900">
          {t('orgs.title')}
        </h2>
        <PermissionGate permission={Permission.ORGANIZATION_CREATE}>
          <Link
            to="/organizations"
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            {t('orgs.new')}
          </Link>
        </PermissionGate>
      </div>

      <div className="mb-4">
        <input
          type="text"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value)
            setPage(0)
          }}
          placeholder={t('common.search')}
          className="w-full max-w-md rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500 focus:outline-none"
        />
      </div>

      {isLoading && <LoadingSpinner />}

      {data && data.items.length === 0 && <EmptyState />}

      {data && data.items.length > 0 && (
        <>
          <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {isAr ? t('common.name_ar') : t('common.name_en')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('common.vat_number')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('orgs.clubs')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('orgs.active_members')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('common.created_at')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {data.items.map((org) => (
                  <tr key={org.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <Link
                        to="/organizations/$orgId"
                        params={{ orgId: org.id }}
                        className="text-sm font-medium text-blue-600 hover:text-blue-800"
                      >
                        {isAr ? org.nameAr : org.nameEn}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {org.vatNumber ?? '-'}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {org.clubCount}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {org.activeMemberCount}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-500">
                      {new Intl.DateTimeFormat(i18n.language, {
                        dateStyle: 'medium',
                        timeZone: 'Asia/Riyadh',
                      }).format(new Date(org.createdAt))}
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
