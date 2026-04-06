import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { listClubRoles, createClubRole, roleKeys } from '@/api/roles'
import { PageShell } from '@/components/layout/PageShell'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { PermissionGate } from '@/components/shared/PermissionGate'
import { Permission } from '@/types/permissions'

export const Route = createFileRoute('/settings/roles/')({
  component: ClubRolesPage,
})

function ClubRolesPage() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [newName, setNewName] = useState('')
  const isAr = i18n.language === 'ar'

  const { data: roles, isLoading } = useQuery({
    queryKey: roleKeys.list(),
    queryFn: listClubRoles,
    staleTime: 120_000,
  })

  const createMutation = useMutation({
    mutationFn: (name: string) => createClubRole({ name }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: roleKeys.all })
      setShowCreate(false)
      setNewName('')
    },
  })

  return (
    <PageShell
      title={t('roles.title')}
      actions={
        <PermissionGate permission={Permission.ROLE_CREATE}>
          <button
            type="button"
            onClick={() => setShowCreate(true)}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700"
          >
            {t('roles.new')}
          </button>
        </PermissionGate>
      }
    >
      {showCreate && (
        <div className="mb-4 flex items-center gap-2 rounded-md border border-gray-200 bg-white p-4">
          <input
            type="text"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            placeholder={t('roles.name_placeholder')}
            className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500 focus:outline-none"
          />
          <button
            type="button"
            onClick={() => createMutation.mutate(newName)}
            disabled={!newName.trim() || createMutation.isPending}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {t('common.actions')}
          </button>
          <button
            type="button"
            onClick={() => {
              setShowCreate(false)
              setNewName('')
            }}
            className="rounded-md px-4 py-2 text-sm font-medium text-gray-600 hover:bg-gray-100"
          >
            {t('common.cancel')}
          </button>
        </div>
      )}

      {isLoading && <LoadingSkeleton rows={5} />}

      {roles && roles.length === 0 && (
        <div className="py-12 text-center text-sm text-gray-500">
          {t('roles.empty')}
        </div>
      )}

      {roles && roles.length > 0 && (
        <div className="overflow-hidden rounded-lg border border-gray-200 bg-white shadow-sm">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                  {t('roles.name')}
                </th>
                <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                  {t('roles.permissions')}
                </th>
                <th className="px-4 py-3 text-start text-xs font-medium uppercase tracking-wider text-gray-500">
                  {t('roles.staff_count')}
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {roles.map((role) => (
                <tr key={role.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2">
                      <Link
                        to="/settings/roles/$roleId"
                        params={{ roleId: role.id }}
                        className="text-sm font-medium text-blue-600 hover:text-blue-800"
                      >
                        {isAr ? role.nameAr : role.nameEn}
                      </Link>
                      {role.isSystem && (
                        <span className="inline-flex items-center rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600">
                          {t('roles.system_badge')}
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600">
                    {role.permissionCount}
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600">
                    {role.staffCount}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </PageShell>
  )
}
