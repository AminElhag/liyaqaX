import { apiClient } from './client'
import type {
  RoleListItem,
  RoleDetail,
  PermissionItem,
} from '@/types/domain'

export async function listRoles(): Promise<RoleListItem[]> {
  const { data } = await apiClient.get<RoleListItem[]>('/nexus/roles')
  return data
}

export async function getRoleDetail(id: string): Promise<RoleDetail> {
  const { data } = await apiClient.get<RoleDetail>(`/nexus/roles/${id}`)
  return data
}

export async function createRole(payload: {
  name: string
  description?: string
}): Promise<RoleDetail> {
  const { data } = await apiClient.post<RoleDetail>('/nexus/roles', payload)
  return data
}

export async function updateRole(
  id: string,
  payload: { name?: string; description?: string },
): Promise<RoleDetail> {
  const { data } = await apiClient.patch<RoleDetail>(
    `/nexus/roles/${id}`,
    payload,
  )
  return data
}

export async function deleteRole(id: string): Promise<void> {
  await apiClient.delete(`/nexus/roles/${id}`)
}

export async function listPermissions(): Promise<PermissionItem[]> {
  const { data } = await apiClient.get<PermissionItem[]>('/nexus/permissions')
  return data
}

export async function replacePermissions(
  roleId: string,
  permissionIds: string[],
): Promise<PermissionItem[]> {
  const { data } = await apiClient.put<PermissionItem[]>(
    `/nexus/roles/${roleId}/permissions`,
    { permissionIds },
  )
  return data
}
