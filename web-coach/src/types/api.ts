/** RFC 7807 Problem Details error shape */
export interface ApiError {
  type: string
  title: string
  status: number
  detail: string
  instance: string
}

/** Login response from POST /api/v1/auth/login */
export interface LoginResponse {
  accessToken: string
  userId: string
  scope: string
  roleId: string
  roleName: string
  organizationId: string | null
  clubId: string | null
  trainerId: string | null
  trainerTypes: string[] | null
  branchIds: string[] | null
}
