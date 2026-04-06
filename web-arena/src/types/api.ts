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
