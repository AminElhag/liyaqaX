import { apiClient } from './client'

export interface SubscriptionPlanResponse {
  id: string
  name: string
  monthlyPriceHalalas: number
  monthlyPriceSar: string
  maxBranches: number
  maxStaff: number
  features: string | null
  isActive: boolean
}

export interface ClubSubscriptionResponse {
  id: string
  clubId: string
  planName: string
  monthlyPriceHalalas: number
  monthlyPriceSar: string
  status: string
  currentPeriodStart: string
  currentPeriodEnd: string
  gracePeriodEndsAt: string
  cancelledAt: string | null
  createdAt: string
}

export interface SubscriptionDashboardItem {
  clubId: string
  clubName: string
  planName: string
  status: string
  currentPeriodEnd: string
  gracePeriodEndsAt: string
  daysUntilExpiry: number
  monthlyPriceSar: string
}

export interface SubscriptionDashboardResponse {
  subscriptions: SubscriptionDashboardItem[]
  totalCount: number
  page: number
  pageSize: number
}

export interface ExpiringSubscriptionItem {
  clubId: string
  clubName: string
  planName: string
  status: string
  currentPeriodEnd: string
  daysUntilExpiry: number
}

export interface ExpiringSubscriptionsResponse {
  expiringSoon: ExpiringSubscriptionItem[]
}

// Plan CRUD
export async function listPlans(): Promise<SubscriptionPlanResponse[]> {
  const { data } = await apiClient.get<SubscriptionPlanResponse[]>('/nexus/subscription-plans')
  return data
}

export async function createPlan(payload: {
  name: string
  monthlyPriceHalalas: number
  maxBranches: number
  maxStaff: number
  features?: string
}): Promise<SubscriptionPlanResponse> {
  const { data } = await apiClient.post<SubscriptionPlanResponse>('/nexus/subscription-plans', payload)
  return data
}

export async function updatePlan(
  planId: string,
  payload: {
    name?: string
    monthlyPriceHalalas?: number
    maxBranches?: number
    maxStaff?: number
    features?: string
  },
): Promise<SubscriptionPlanResponse> {
  const { data } = await apiClient.patch<SubscriptionPlanResponse>(
    `/nexus/subscription-plans/${planId}`,
    payload,
  )
  return data
}

export async function deletePlan(planId: string): Promise<void> {
  await apiClient.delete(`/nexus/subscription-plans/${planId}`)
}

// Club subscription management
export async function assignSubscription(
  clubId: string,
  payload: { planPublicId: string; periodStartDate: string; periodMonths: number },
): Promise<ClubSubscriptionResponse> {
  const { data } = await apiClient.post<ClubSubscriptionResponse>(
    `/nexus/clubs/${clubId}/subscription`,
    payload,
  )
  return data
}

export async function getClubSubscription(clubId: string): Promise<ClubSubscriptionResponse> {
  const { data } = await apiClient.get<ClubSubscriptionResponse>(
    `/nexus/clubs/${clubId}/subscription`,
  )
  return data
}

export async function extendSubscription(
  clubId: string,
  additionalMonths: number,
): Promise<ClubSubscriptionResponse> {
  const { data } = await apiClient.post<ClubSubscriptionResponse>(
    `/nexus/clubs/${clubId}/subscription/extend`,
    { additionalMonths },
  )
  return data
}

export async function cancelSubscription(clubId: string): Promise<ClubSubscriptionResponse> {
  const { data } = await apiClient.post<ClubSubscriptionResponse>(
    `/nexus/clubs/${clubId}/subscription/cancel`,
  )
  return data
}

// Dashboard
export async function getSubscriptionDashboard(
  page = 0,
  pageSize = 20,
): Promise<SubscriptionDashboardResponse> {
  const { data } = await apiClient.get<SubscriptionDashboardResponse>('/nexus/subscriptions', {
    params: { page, pageSize },
  })
  return data
}

export async function getExpiringSubscriptions(): Promise<ExpiringSubscriptionsResponse> {
  const { data } = await apiClient.get<ExpiringSubscriptionsResponse>(
    '/nexus/subscriptions/expiring',
  )
  return data
}
