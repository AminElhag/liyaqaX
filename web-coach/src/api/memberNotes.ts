import { apiClient } from './client'

export interface CreateNoteRequest {
  noteType: string
  content: string
}

export interface NoteResponse {
  noteId: string
  noteType: string
  content: string
  followUpAt: string | null
  createdByName: string
  createdAt: string
}

export async function createCoachNote(
  memberId: string,
  request: CreateNoteRequest,
): Promise<NoteResponse> {
  const { data } = await apiClient.post<NoteResponse>(
    `/coach/members/${memberId}/notes`,
    request,
  )
  return data
}

export async function listCoachNotes(
  memberId: string,
): Promise<NoteResponse[]> {
  const { data } = await apiClient.get<NoteResponse[]>(
    `/coach/members/${memberId}/notes`,
  )
  return data
}

export const coachNoteKeys = {
  all: ['coach-notes'] as const,
  list: (memberId: string) => [...coachNoteKeys.all, memberId] as const,
}
