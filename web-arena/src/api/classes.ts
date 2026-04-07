import { apiClient } from './client'

// ── Types ──────────────────────────────────────────────────────────────────────

export interface GxScheduleItem {
  id: string
  classType: { name: string; nameAr: string; color: string | null }
  instructorName: string
  startTime: string
  endTime: string
  capacity: number
  bookedCount: number
  spotsRemaining: number
  isBooked: boolean
  waitlistStatus: string | null
  waitlistPosition: number | null
  waitlistOfferExpiresAt: string | null
}

export interface GxBooking {
  id: string
  instanceId: string
  className: string
  classNameAr: string
  instructorName: string
  startTime: string
  status: string
  bookedAt: string
  cancelledAt: string | null
}

export interface WaitlistJoinResponse {
  entryId: string
  position: number
  status: string
  message: string
}

export interface WaitlistAcceptResponse {
  bookingId: string
  message: string
}

// ── API calls ──────────────────────────────────────────────────────────────────

export async function getSchedule(): Promise<GxScheduleItem[]> {
  const { data } = await apiClient.get<GxScheduleItem[]>('/arena/gx/schedule')
  return data
}

export async function bookClass(instanceId: string): Promise<GxBooking> {
  const { data } = await apiClient.post<GxBooking>(`/arena/gx/${instanceId}/book`)
  return data
}

export async function cancelBooking(instanceId: string): Promise<void> {
  await apiClient.delete(`/arena/gx/${instanceId}/book`)
}

export async function getBookings(): Promise<GxBooking[]> {
  const { data } = await apiClient.get<GxBooking[]>('/arena/gx/bookings')
  return data
}

// ── Waitlist API calls ─────────────────────────────────────────────────────────

export async function joinWaitlist(instanceId: string): Promise<WaitlistJoinResponse> {
  const { data } = await apiClient.post<WaitlistJoinResponse>(
    `/arena/gx/${instanceId}/waitlist`,
  )
  return data
}

export async function leaveWaitlist(instanceId: string): Promise<void> {
  await apiClient.delete(`/arena/gx/${instanceId}/waitlist`)
}

export async function acceptWaitlistOffer(instanceId: string): Promise<GxBooking> {
  const { data } = await apiClient.post<GxBooking>(
    `/arena/gx/${instanceId}/waitlist/accept`,
  )
  return data
}

// ── Query keys ─────────────────────────────────────────────────────────────────

export const classKeys = {
  all: ['classes'] as const,
  schedule: () => [...classKeys.all, 'schedule'] as const,
  bookings: () => [...classKeys.all, 'bookings'] as const,
}
