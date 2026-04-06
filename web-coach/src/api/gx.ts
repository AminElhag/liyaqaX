import { apiClient } from './client'
import type { GxClass, GxBooking } from '@/types/domain'

export async function getGxClasses(from?: string, to?: string): Promise<GxClass[]> {
  const { data } = await apiClient.get<GxClass[]>('/coach/gx/classes', { params: { from, to } })
  return data
}

export async function getClassBookings(classId: string): Promise<GxBooking[]> {
  const { data } = await apiClient.get<GxBooking[]>(`/coach/gx/classes/${classId}/bookings`)
  return data
}

export async function markGxAttendance(
  classId: string,
  attendances: { bookingId: string; attended: boolean }[],
): Promise<void> {
  await apiClient.patch(`/coach/gx/classes/${classId}/attendance`, { attendances })
}
