import { createFileRoute, Link, Outlet, useMatches } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getOrg } from '@/api/organizations'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { PermissionGate } from '@/components/common/PermissionGate'
import { Permission } from '@/types/permissions'

export const Route = createFileRoute('/organizations/$orgId')({
  component: OrgDetailLayout,
})

function OrgDetailLayout() {
  const matches = useMatches()
  // If a child route is matched (club or branch detail), render the Outlet instead
  const hasChildRoute = matches.some(
    (m) => m.routeId !== '/organizations/$orgId' && m.routeId.startsWith('/organizations/$orgId/'),
  )

  if (hasChildRoute) {
    return <Outlet />
  }

  return <OrgDetailPage />
}

function OrgDetailPage() {
  const { orgId } = Route.useParams()
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'

  const { data: org, isLoading } = useQuery({
    queryKey: ['organization', orgId],
    queryFn: () => getOrg(orgId),
    staleTime: 120_000,
  })

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (!org) {
    return null
  }

  return (
    <div className="p-6">
      <div className="mb-2">
        <Link
          to="/organizations"
          className="text-sm text-blue-600 hover:text-blue-800"
        >
          &larr; {t('common.back')}
        </Link>
      </div>

      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold text-gray-900">
            {isAr ? org.nameAr : org.nameEn}
          </h2>
          <p className="mt-1 text-sm text-gray-500">
            {t('orgs.detail')}
          </p>
        </div>
        <div className="flex gap-2">
          <PermissionGate permission={Permission.ORGANIZATION_UPDATE}>
            <button
              type="button"
              className="rounded-md border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              {t('orgs.edit')}
            </button>
          </PermissionGate>
          <PermissionGate permission={Permission.CLUB_CREATE}>
            <button
              type="button"
              className="rounded-md bg-blue-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-blue-700"
            >
              {t('orgs.add_club')}
            </button>
          </PermissionGate>
        </div>
      </div>

      <div className="mb-6 rounded-lg border border-gray-200 bg-white p-5 shadow-sm">
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <dt className="text-sm font-medium text-gray-500">{t('common.name_en')}</dt>
            <dd className="mt-1 text-sm text-gray-900">{org.nameEn}</dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-gray-500">{t('common.name_ar')}</dt>
            <dd className="mt-1 text-sm text-gray-900">{org.nameAr}</dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-gray-500">{t('common.vat_number')}</dt>
            <dd className="mt-1 text-sm text-gray-900">{org.vatNumber ?? '-'}</dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-gray-500">{t('common.created_at')}</dt>
            <dd className="mt-1 text-sm text-gray-900">
              {new Intl.DateTimeFormat(i18n.language, {
                dateStyle: 'medium',
                timeStyle: 'short',
                timeZone: 'Asia/Riyadh',
              }).format(new Date(org.createdAt))}
            </dd>
          </div>
        </dl>
      </div>

      <div>
        <h3 className="mb-3 text-lg font-semibold text-gray-900">
          {t('orgs.clubs')}
        </h3>

        {org.clubs.length === 0 ? (
          <p className="text-sm text-gray-500">{t('common.no_results')}</p>
        ) : (
          <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {isAr ? t('common.name_ar') : t('common.name_en')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('clubs.branches')}
                  </th>
                  <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                    {t('orgs.active_members')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {org.clubs.map((club) => (
                  <tr key={club.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <Link
                        to="/organizations/$orgId/clubs/$clubId"
                        params={{ orgId: org.id, clubId: club.id }}
                        className="text-sm font-medium text-blue-600 hover:text-blue-800"
                      >
                        {isAr ? club.nameAr : club.nameEn}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {club.branchCount}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {club.activeMemberCount}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
