import { apiClient } from './client'

// ── Types ────────────────────────────────────────────────────────────────────

export interface RosterShiftItem {
  shiftId: string
  staffMemberId: string
  staffMemberName: string
  startAt: string
  endAt: string
  notes: string | null
  hasPendingSwap: boolean
}

export interface RosterResponse {
  branchName: string
  weekStart: string
  weekEnd: string
  shifts: RosterShiftItem[]
}

export interface MyShiftItem {
  shiftId: string
  branchName: string
  startAt: string
  endAt: string
  notes: string | null
  swapRequest: {
    swapId: string
    targetStaffName: string
    status: string
  } | null
}

export interface MyShiftsResponse {
  shifts: MyShiftItem[]
}

export interface ShiftResponse {
  shiftId: string
  staffMemberName: string
  branchName: string
  startAt: string
  endAt: string
  notes: string | null
}

export interface PendingSwapItem {
  swapId: string
  shiftDate: string
  shiftStart: string
  shiftEnd: string
  requesterName: string
  targetName: string
  status: string
  requesterNote: string | null
}

export interface PendingSwapsResponse {
  swapRequests: PendingSwapItem[]
}

export interface CreateShiftRequest {
  staffMemberPublicId: string
  branchPublicId: string
  startAt: string
  endAt: string
  notes?: string
}

export interface CreateSwapRequest {
  targetStaffPublicId: string
  requesterNote?: string
}

// ── API calls ────────────────────────────────────────────────────────────────

export async function getRoster(
  branchPublicId: string,
  weekStart: string,
): Promise<RosterResponse> {
  const { data } = await apiClient.get<RosterResponse>('/pulse/shifts', {
    params: { branchPublicId, weekStart },
  })
  return data
}

export async function getMyShifts(): Promise<MyShiftsResponse> {
  const { data } = await apiClient.get<MyShiftsResponse>('/pulse/shifts/my')
  return data
}

export async function createShift(
  request: CreateShiftRequest,
): Promise<ShiftResponse> {
  const { data } = await apiClient.post<ShiftResponse>('/pulse/shifts', request)
  return data
}

export async function updateShift(
  shiftId: string,
  request: { startAt?: string; endAt?: string; notes?: string },
): Promise<ShiftResponse> {
  const { data } = await apiClient.patch<ShiftResponse>(
    `/pulse/shifts/${shiftId}`,
    request,
  )
  return data
}

export async function deleteShift(shiftId: string): Promise<void> {
  await apiClient.delete(`/pulse/shifts/${shiftId}`)
}

export async function requestSwap(
  shiftId: string,
  request: CreateSwapRequest,
): Promise<{ swapId: string }> {
  const { data } = await apiClient.post<{ swapId: string }>(
    `/pulse/shifts/${shiftId}/swap-requests`,
    request,
  )
  return data
}

export async function respondToSwap(
  swapId: string,
  action: 'accept' | 'decline',
): Promise<void> {
  await apiClient.patch(`/pulse/shifts/swap-requests/${swapId}/respond`, {
    action,
  })
}

export async function resolveSwap(
  swapId: string,
  action: 'approve' | 'reject',
): Promise<void> {
  await apiClient.patch(`/pulse/shifts/swap-requests/${swapId}/resolve`, {
    action,
  })
}

export async function getPendingSwaps(): Promise<PendingSwapsResponse> {
  const { data } = await apiClient.get<PendingSwapsResponse>(
    '/pulse/shifts/swap-requests/pending',
  )
  return data
}

// ── Query keys ───────────────────────────────────────────────────────────────

export const shiftKeys = {
  all: ['shifts'] as const,
  roster: (branchId: string, weekStart: string) =>
    [...shiftKeys.all, 'roster', branchId, weekStart] as const,
  my: () => [...shiftKeys.all, 'my'] as const,
  pendingSwaps: () => [...shiftKeys.all, 'pending-swaps'] as const,
}
