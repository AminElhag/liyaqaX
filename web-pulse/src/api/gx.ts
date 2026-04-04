import { apiClient } from './client'
import type { PageResponse } from '@/types/api'
import type {
  GXClassType,
  GXClassInstance,
  GXBooking,
  GXAttendance,
  CreateGXClassTypeRequest,
  CreateGXClassInstanceRequest,
  BookMemberRequest,
  BulkAttendanceRequest,
} from '@/types/domain'

// ── Class Types ─────────────────────────────────────────────────────────────

export async function createClassType(
  request: CreateGXClassTypeRequest,
): Promise<GXClassType> {
  const { data } = await apiClient.post<GXClassType>(
    '/gx/class-types',
    request,
  )
  return data
}

export async function getClassTypes(params: {
  page?: number
  size?: number
} = {}): Promise<PageResponse<GXClassType>> {
  const { data } = await apiClient.get<PageResponse<GXClassType>>(
    '/gx/class-types',
    { params },
  )
  return data
}

export async function getClassType(id: string): Promise<GXClassType> {
  const { data } = await apiClient.get<GXClassType>(`/gx/class-types/${id}`)
  return data
}

export async function updateClassType(
  id: string,
  request: CreateGXClassTypeRequest,
): Promise<GXClassType> {
  const { data } = await apiClient.patch<GXClassType>(
    `/gx/class-types/${id}`,
    request,
  )
  return data
}

export async function deleteClassType(id: string): Promise<void> {
  await apiClient.delete(`/gx/class-types/${id}`)
}

// ── Class Instances ─────────────────────────────────────────────────────────

export interface ClassInstanceListParams {
  branchId: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export async function createClassInstance(
  branchId: string,
  request: CreateGXClassInstanceRequest,
): Promise<GXClassInstance> {
  const { data } = await apiClient.post<GXClassInstance>(
    '/gx/classes',
    request,
    { params: { branchId } },
  )
  return data
}

export async function getClassInstances(
  params: ClassInstanceListParams,
): Promise<PageResponse<GXClassInstance>> {
  const { data } = await apiClient.get<PageResponse<GXClassInstance>>(
    '/gx/classes',
    { params },
  )
  return data
}

export async function getClassInstance(
  id: string,
): Promise<GXClassInstance> {
  const { data } = await apiClient.get<GXClassInstance>(`/gx/classes/${id}`)
  return data
}

export async function updateClassInstance(
  id: string,
  request: CreateGXClassInstanceRequest,
): Promise<GXClassInstance> {
  const { data } = await apiClient.patch<GXClassInstance>(
    `/gx/classes/${id}`,
    request,
  )
  return data
}

export async function cancelClassInstance(
  id: string,
): Promise<GXClassInstance> {
  const { data } = await apiClient.post<GXClassInstance>(
    `/gx/classes/${id}/cancel`,
  )
  return data
}

// ── Bookings ────────────────────────────────────────────────────────────────

export async function bookMember(
  classId: string,
  request: BookMemberRequest,
): Promise<GXBooking> {
  const { data } = await apiClient.post<GXBooking>(
    `/gx/classes/${classId}/bookings`,
    request,
  )
  return data
}

export async function getClassBookings(
  classId: string,
  params: { page?: number; size?: number } = {},
): Promise<PageResponse<GXBooking>> {
  const { data } = await apiClient.get<PageResponse<GXBooking>>(
    `/gx/classes/${classId}/bookings`,
    { params },
  )
  return data
}

export async function cancelBooking(
  classId: string,
  bookingId: string,
): Promise<GXBooking> {
  const { data } = await apiClient.delete<GXBooking>(
    `/gx/classes/${classId}/bookings/${bookingId}`,
  )
  return data
}

export async function getClassWaitlist(
  classId: string,
): Promise<GXBooking[]> {
  const { data } = await apiClient.get<GXBooking[]>(
    `/gx/classes/${classId}/waitlist`,
  )
  return data
}

// ── Attendance ──────────────────────────────────────────────────────────────

export async function submitAttendance(
  classId: string,
  request: BulkAttendanceRequest,
): Promise<GXAttendance[]> {
  const { data } = await apiClient.post<GXAttendance[]>(
    `/gx/classes/${classId}/attendance`,
    request,
  )
  return data
}

export async function getClassAttendance(
  classId: string,
): Promise<GXAttendance[]> {
  const { data } = await apiClient.get<GXAttendance[]>(
    `/gx/classes/${classId}/attendance`,
  )
  return data
}

// ── Member GX History ───────────────────────────────────────────────────────

export async function getMemberGXBookings(
  memberId: string,
  params: { page?: number; size?: number } = {},
): Promise<PageResponse<GXBooking>> {
  const { data } = await apiClient.get<PageResponse<GXBooking>>(
    `/gx/members/${memberId}/gx-bookings`,
    { params },
  )
  return data
}

// ── Query key factories for TanStack Query ──────────────────────────────────

export const gxKeys = {
  all: ['gx'] as const,
  classTypes: () => [...gxKeys.all, 'class-types'] as const,
  classTypeList: (params?: Record<string, unknown>) =>
    [...gxKeys.classTypes(), 'list', params] as const,
  classTypeDetail: (id: string) =>
    [...gxKeys.classTypes(), 'detail', id] as const,
  instances: () => [...gxKeys.all, 'instances'] as const,
  instanceList: (params: ClassInstanceListParams) =>
    [...gxKeys.instances(), 'list', params] as const,
  instanceDetail: (id: string) =>
    [...gxKeys.instances(), 'detail', id] as const,
  bookings: (classId: string) =>
    [...gxKeys.instanceDetail(classId), 'bookings'] as const,
  waitlist: (classId: string) =>
    [...gxKeys.instanceDetail(classId), 'waitlist'] as const,
  attendance: (classId: string) =>
    [...gxKeys.instanceDetail(classId), 'attendance'] as const,
  memberBookings: (memberId: string) =>
    [...gxKeys.all, 'member-bookings', memberId] as const,
}
