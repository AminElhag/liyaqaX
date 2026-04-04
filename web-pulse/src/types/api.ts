/** RFC 7807 Problem Details error shape */
export interface ApiError {
  type: string
  title: string
  status: number
  detail: string
  instance: string
}

/** Backend pagination wrapper */
export interface PaginationMeta {
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export interface PageResponse<T> {
  items: T[]
  pagination: PaginationMeta
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
  branchIds: string[] | null
}

/** Response from GET /api/v1/auth/me */
export interface AuthMeResponse {
  userId: string
  email: string
  scope: string
  roleId: string
  roleName: string
  permissions: string[]
  organizationId: string | null
  clubId: string | null
  branchIds: string[]
}
