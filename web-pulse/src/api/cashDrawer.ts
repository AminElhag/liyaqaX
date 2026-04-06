import { apiClient } from './client'
import type { PageResponse } from '@/types/api'
import type {
  CashDrawerSession,
  CashDrawerSessionSummary,
  CashDrawerEntry,
  OpenSessionRequest,
  CloseSessionRequest,
  ReconcileSessionRequest,
  CreateEntryRequest,
} from '@/types/domain'

const BASE = '/cash-drawer/sessions'

// ── Session params ─────────────────────────────────────────────────────────

export interface SessionListParams {
  page?: number
  size?: number
  sort?: string
  order?: 'asc' | 'desc'
  status?: string
  branchId?: string
}

// ── Session API calls ──────────────────────────────────────────────────────

export async function openSession(
  branchId: string,
  request: OpenSessionRequest,
): Promise<CashDrawerSession> {
  const { data } = await apiClient.post<CashDrawerSession>(
    BASE,
    request,
    { params: { branchId } },
  )
  return data
}

export async function getSessionList(
  params: SessionListParams = {},
): Promise<PageResponse<CashDrawerSessionSummary>> {
  const { data } = await apiClient.get<PageResponse<CashDrawerSessionSummary>>(
    BASE,
    { params },
  )
  return data
}

export async function getCurrentSession(
  branchId: string,
): Promise<CashDrawerSession | null> {
  try {
    const { data } = await apiClient.get<CashDrawerSession>(
      `${BASE}/current`,
      { params: { branchId } },
    )
    return data
  } catch {
    // 204 No Content means no open session
    return null
  }
}

export async function getSession(
  id: string,
): Promise<CashDrawerSession> {
  const { data } = await apiClient.get<CashDrawerSession>(
    `${BASE}/${id}`,
  )
  return data
}

export async function closeSession(
  id: string,
  request: CloseSessionRequest,
): Promise<CashDrawerSession> {
  const { data } = await apiClient.patch<CashDrawerSession>(
    `${BASE}/${id}/close`,
    request,
  )
  return data
}

export async function reconcileSession(
  id: string,
  request: ReconcileSessionRequest,
): Promise<CashDrawerSession> {
  const { data } = await apiClient.patch<CashDrawerSession>(
    `${BASE}/${id}/reconcile`,
    request,
  )
  return data
}

// ── Entry API calls ────────────────────────────────────────────────────────

export async function addEntry(
  sessionId: string,
  request: CreateEntryRequest,
): Promise<CashDrawerEntry> {
  const { data } = await apiClient.post<CashDrawerEntry>(
    `${BASE}/${sessionId}/entries`,
    request,
  )
  return data
}

export async function getEntries(
  sessionId: string,
): Promise<CashDrawerEntry[]> {
  const { data } = await apiClient.get<CashDrawerEntry[]>(
    `${BASE}/${sessionId}/entries`,
  )
  return data
}

// ── Query key factories for TanStack Query ─────────────────────────────────

export const cashDrawerKeys = {
  all: ['cash-drawer'] as const,
  sessions: () => [...cashDrawerKeys.all, 'sessions'] as const,
  sessionList: (params: SessionListParams) =>
    [...cashDrawerKeys.sessions(), 'list', params] as const,
  currentSession: (branchId: string) =>
    [...cashDrawerKeys.sessions(), 'current', branchId] as const,
  sessionDetail: (id: string) =>
    [...cashDrawerKeys.sessions(), 'detail', id] as const,
  entries: (sessionId: string) =>
    [...cashDrawerKeys.sessionDetail(sessionId), 'entries'] as const,
}
