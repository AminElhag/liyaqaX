import { apiClient } from './client'

export interface CheckInRequest {
  memberPublicId: string
  method: 'staff_phone' | 'staff_name' | 'qr_scan'
}

export interface CheckInResponse {
  checkInId: string
  memberName: string
  memberPhone: string
  membershipPlan: string | null
  checkedInAt: string
  branchName: string
  method: string
  todayCount: number
}

export interface TodayCountResponse {
  count: number
  branchName: string
  date: string
}

export interface RecentCheckInItem {
  checkInId: string
  memberName: string
  memberPhone: string
  method: string
  checkedInAt: string
}

export interface RecentCheckInsResponse {
  checkIns: RecentCheckInItem[]
}

export async function checkInMember(
  request: CheckInRequest,
): Promise<CheckInResponse> {
  const { data } = await apiClient.post<CheckInResponse>(
    '/pulse/check-in',
    request,
  )
  return data
}

export async function getTodayCount(): Promise<TodayCountResponse> {
  const { data } = await apiClient.get<TodayCountResponse>(
    '/pulse/check-in/today-count',
  )
  return data
}

export async function getRecentCheckIns(): Promise<RecentCheckInsResponse> {
  const { data } = await apiClient.get<RecentCheckInsResponse>(
    '/pulse/check-in/recent',
  )
  return data
}

export const checkInKeys = {
  all: ['check-in'] as const,
  todayCount: () => [...checkInKeys.all, 'today-count'] as const,
  recent: () => [...checkInKeys.all, 'recent'] as const,
}
