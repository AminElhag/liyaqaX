import { createFileRoute, Link, useNavigate } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  getRoleDetail,
  updateRole,
  deleteRole,
  listPermissions,
  replacePermissions,
} from '@/api/roles'
import { LoadingSpinner } from '@/components/common/LoadingSpinner'
import { PermissionGate } from '@/components/common/PermissionGate'
import { Permission } from '@/types/permissions'

export const Route = createFileRoute('/roles/$roleId')({
  component: RoleDetailPage,
})

function RoleDetailPage() {
  const { roleId } = Route.useParams()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [editingName, setEditingName] = useState(false)
  const [nameValue, setNameValue] = useState('')
  const [selectedPermIds, setSelectedPermIds] = useState<Set<string>>(new Set())
  const [permsDirty, setPermsDirty] = useState(false)
  const [toast, setToast] = useState<string | null>(null)

  const { data: role, isLoading: roleLoading } = useQuery({
    queryKey: ['role', roleId],
    queryFn: () => getRoleDetail(roleId),
    staleTime: 120_000,
  })

  const { data: allPermissions } = useQuery({
    queryKey: ['permissions'],
    queryFn: listPermissions,
    staleTime: 300_000,
  })

  // Initialize selected permissions when role data loads
  const initialPermIds = useMemo(() => {
    if (!role) return new Set<string>()
    return new Set(role.permissions.map((p) => p.id))
  }, [role])

  // Use initial if not dirty
  const activePermIds = permsDirty ? selectedPermIds : initialPermIds

  // Group permissions by domain prefix
  const groupedPermissions = useMemo(() => {
    if (!allPermissions) return {}
    const groups: Record<string, typeof allPermissions> = {}
    for (const perm of allPermissions) {
      const domain = perm.code.split(':')[0]
      if (!groups[domain]) groups[domain] = []
      groups[domain].push(perm)
    }
    return groups
  }, [allPermissions])

  const updateNameMutation = useMutation({
    mutationFn: (name: string) => updateRole(roleId, { name }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['role', roleId] })
      queryClient.invalidateQueries({ queryKey: ['roles'] })
      setEditingName(false)
      showToast(t('roles.updated'))
    },
  })

  const savePermsMutation = useMutation({
    mutationFn: (permissionIds: string[]) =>
      replacePermissions(roleId, permissionIds),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['role', roleId] })
      queryClient.invalidateQueries({ queryKey: ['roles'] })
      setPermsDirty(false)
      showToast(t('roles.permissions_updated'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => deleteRole(roleId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['roles'] })
      navigate({ to: '/roles' })
    },
  })

  function togglePermission(permId: string) {
    const next = new Set(activePermIds)
    if (next.has(permId)) {
      next.delete(permId)
    } else {
      next.add(permId)
    }
    setSelectedPermIds(next)
    setPermsDirty(true)
  }

  function showToast(msg: string) {
    setToast(msg)
    setTimeout(() => setToast(null), 5000)
  }

  if (roleLoading) return <LoadingSpinner />
  if (!role) return null

  return (
    <div className="p-6">
      {toast && (
        <div className="fixed top-4 end-4 z-50 rounded-md bg-green-50 px-4 py-3 text-sm text-green-700 shadow-md">
          {toast}
        </div>
      )}

      <Link
        to="/roles"
        className="mb-4 inline-block text-sm text-blue-600 hover:text-blue-800"
      >
        &larr; {t('common.back')}
      </Link>

      {/* Role header */}
      <div className="mb-6 rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            {editingName ? (
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  value={nameValue}
                  onChange={(e) => setNameValue(e.target.value)}
                  className="rounded-md border border-gray-300 px-3 py-1 text-lg font-semibold focus:border-blue-500 focus:ring-1 focus:ring-blue-500 focus:outline-none"
                />
                <button
                  type="button"
                  onClick={() => updateNameMutation.mutate(nameValue)}
                  disabled={updateNameMutation.isPending}
                  className="rounded-md bg-blue-600 px-3 py-1 text-sm text-white hover:bg-blue-700"
                >
                  {t('common.save')}
                </button>
                <button
                  type="button"
                  onClick={() => setEditingName(false)}
                  className="text-sm text-gray-500 hover:text-gray-700"
                >
                  {t('common.cancel')}
                </button>
              </div>
            ) : (
              <>
                <h2 className="text-xl font-semibold text-gray-900">
                  {role.nameEn}
                </h2>
                {role.isSystem && (
                  <span className="inline-flex items-center rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600">
                    {t('roles.system_badge')}
                  </span>
                )}
                <PermissionGate permission={Permission.ROLE_UPDATE}>
                  <button
                    type="button"
                    onClick={() => {
                      setNameValue(role.nameEn)
                      setEditingName(true)
                    }}
                    className="text-sm text-blue-600 hover:text-blue-800"
                  >
                    {t('common.edit')}
                  </button>
                </PermissionGate>
              </>
            )}
          </div>

          {!role.isSystem && (
            <PermissionGate permission={Permission.ROLE_DELETE}>
              <button
                type="button"
                onClick={() => {
                  if (window.confirm(t('roles.delete_confirm'))) {
                    deleteMutation.mutate()
                  }
                }}
                disabled={deleteMutation.isPending}
                className="rounded-md border border-red-200 px-3 py-1.5 text-sm font-medium text-red-600 hover:bg-red-50"
              >
                {t('roles.delete')}
              </button>
            </PermissionGate>
          )}
        </div>

        <div className="mt-2 text-sm text-gray-500">
          {t('roles.scope')}: {role.scope} &middot; {t('roles.staff_count')}:{' '}
          {role.staffCount}
        </div>
      </div>

      {/* Permissions section */}
      <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold text-gray-900">
            {t('roles.permissions_section')}
          </h3>
          <PermissionGate permission={Permission.ROLE_UPDATE}>
            <button
              type="button"
              onClick={() =>
                savePermsMutation.mutate(Array.from(activePermIds))
              }
              disabled={!permsDirty || savePermsMutation.isPending}
              className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {t('roles.save_permissions')}
            </button>
          </PermissionGate>
        </div>

        <div className="space-y-6">
          {Object.entries(groupedPermissions).map(([domain, perms]) => (
            <div key={domain}>
              <h4 className="mb-2 text-sm font-semibold capitalize text-gray-700">
                {domain}
              </h4>
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4">
                {perms.map((perm) => (
                  <label
                    key={perm.id}
                    className="flex items-center gap-2 rounded px-2 py-1 text-sm hover:bg-gray-50"
                  >
                    <input
                      type="checkbox"
                      checked={activePermIds.has(perm.id)}
                      onChange={() => togglePermission(perm.id)}
                      className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                    />
                    <span className="text-gray-700">{perm.code}</span>
                  </label>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
