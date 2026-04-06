import { apiClient } from './client'
import type {
  RoleListItem,
  RoleDetail,
  PermissionItem,
} from '@/types/domain'

export async function listClubRoles(): Promise<RoleListItem[]> {
  const { data } = await apiClient.get<RoleListItem[]>('/roles')
  return data
}

export async function getRoleDetail(id: string): Promise<RoleDetail> {
  const { data } = await apiClient.get<RoleDetail>(`/roles/${id}`)
  return data
}

export async function createClubRole(payload: {
  name: string
  description?: string
}): Promise<RoleDetail> {
  const { data } = await apiClient.post<RoleDetail>('/roles', payload)
  return data
}

export async function updateClubRole(
  id: string,
  payload: { name?: string; description?: string },
): Promise<RoleDetail> {
  const { data } = await apiClient.patch<RoleDetail>(`/roles/${id}`, payload)
  return data
}

export async function deleteClubRole(id: string): Promise<void> {
  await apiClient.delete(`/roles/${id}`)
}

export async function listPermissions(): Promise<PermissionItem[]> {
  const { data } = await apiClient.get<PermissionItem[]>('/permissions')
  return data
}

export async function replaceRolePermissions(
  roleId: string,
  permissionIds: string[],
): Promise<PermissionItem[]> {
  const { data } = await apiClient.put<PermissionItem[]>(
    `/roles/${roleId}/permissions`,
    { permissionIds },
  )
  return data
}

export async function assignStaffRole(
  staffId: string,
  roleId: string,
): Promise<void> {
  await apiClient.patch(`/staff/${staffId}/role`, { roleId })
}

export const roleKeys = {
  all: ['roles'] as const,
  list: () => [...roleKeys.all, 'list'] as const,
  details: () => [...roleKeys.all, 'detail'] as const,
  detail: (id: string) => [...roleKeys.details(), id] as const,
  permissions: () => ['permissions'] as const,
}
