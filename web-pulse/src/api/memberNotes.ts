import { apiClient } from './client'

export interface CreateNoteRequest {
  noteType: string
  content: string
  followUpAt?: string
}

export interface NoteResponse {
  noteId: string
  noteType: string
  content: string
  followUpAt: string | null
  createdByName: string
  createdAt: string
}

export interface TimelineEvent {
  eventAt: string
  eventType: string
  // Note fields
  noteId?: string
  content?: string
  noteType?: string
  followUpAt?: string | null
  createdByName?: string
  canDelete?: boolean
  // Membership fields
  membershipId?: string
  planName?: string
  detail?: string
  // Payment fields
  paymentId?: string
  amountSar?: string
  method?: string
}

export interface TimelineResponse {
  events: TimelineEvent[]
  nextCursor: string | null
}

export interface FollowUpItem {
  noteId: string
  followUpAt: string
  content: string
  memberName: string
  memberPublicId: string
  createdByName: string
  daysUntilDue: number
}

export interface FollowUpResponse {
  followUps: FollowUpItem[]
}

export async function createNote(
  memberId: string,
  request: CreateNoteRequest,
): Promise<NoteResponse> {
  const { data } = await apiClient.post<NoteResponse>(
    `/pulse/members/${memberId}/notes`,
    request,
  )
  return data
}

export async function deleteNote(
  memberId: string,
  noteId: string,
): Promise<void> {
  await apiClient.delete(`/pulse/members/${memberId}/notes/${noteId}`)
}

export async function getTimeline(
  memberId: string,
  cursor?: string,
  limit: number = 20,
): Promise<TimelineResponse> {
  const { data } = await apiClient.get<TimelineResponse>(
    `/pulse/members/${memberId}/timeline`,
    { params: { cursor, limit } },
  )
  return data
}

export async function getFollowUps(): Promise<FollowUpResponse> {
  const { data } = await apiClient.get<FollowUpResponse>('/pulse/follow-ups')
  return data
}

export const memberNoteKeys = {
  all: ['member-notes'] as const,
  timeline: (memberId: string) =>
    [...memberNoteKeys.all, 'timeline', memberId] as const,
  followUps: () => [...memberNoteKeys.all, 'follow-ups'] as const,
}
