/** Organization list item returned by GET /api/v1/nexus/organizations */
export interface OrgListItem {
  id: string
  nameEn: string
  nameAr: string
  vatNumber: string | null
  isActive: boolean
  clubCount: number
  activeMemberCount: number
  createdAt: string
}

/** Full organization detail returned by GET /api/v1/nexus/organizations/:id */
export interface OrgDetail {
  id: string
  nameEn: string
  nameAr: string
  vatNumber: string | null
  isActive: boolean
  createdAt: string
  updatedAt: string
  clubs: ClubSummaryItem[]
}

/** Club summary nested inside OrgDetail */
export interface ClubSummaryItem {
  id: string
  nameEn: string
  nameAr: string
  isActive: boolean
  branchCount: number
  activeMemberCount: number
}

/** Club list item returned by GET /api/v1/nexus/organizations/:orgId/clubs */
export interface ClubListItem {
  id: string
  nameEn: string
  nameAr: string
  isActive: boolean
  branchCount: number
  activeMemberCount: number
  createdAt: string
}

/** Full club detail for nexus view */
export interface ClubDetailNexus {
  id: string
  organizationId: string
  nameEn: string
  nameAr: string
  isActive: boolean
  branchCount: number
  activeMemberCount: number
  activeMembershipCount: number
  estimatedMrrHalalas: number
  createdAt: string
  updatedAt: string
  branches: BranchListItem[]
}

/** Branch list item */
export interface BranchListItem {
  id: string
  nameEn: string
  nameAr: string
  isActive: boolean
  activeMemberCount: number
  createdAt: string
}

/** Full branch detail for nexus view */
export interface BranchDetailNexus {
  id: string
  clubId: string
  organizationId: string
  nameEn: string
  nameAr: string
  isActive: boolean
  activeMemberCount: number
  staffCount: number
  trainerCount: number
  createdAt: string
  updatedAt: string
}

/** Member search result item */
export interface MemberSearchItem {
  id: string
  fullNameEn: string
  fullNameAr: string
  email: string | null
  phone: string
  membershipStatus: string
  clubNameEn: string
  clubNameAr: string
  organizationNameEn: string
  organizationNameAr: string
}

/** Full member detail for nexus read-only view */
export interface MemberDetailNexus {
  id: string
  fullNameEn: string
  fullNameAr: string
  email: string | null
  phone: string
  membershipStatus: string
  organizationId: string
  organizationNameEn: string
  organizationNameAr: string
  clubId: string
  clubNameEn: string
  clubNameAr: string
  branchId: string
  branchNameEn: string
  branchNameAr: string
  joinedAt: string
  createdAt: string
}

/** Platform-wide statistics */
export interface PlatformStats {
  totalOrganizations: number
  totalClubs: number
  totalBranches: number
  activeMembers: number
  activeMemberships: number
  estimatedMrrHalalas: number
  newMembersLast30Days: number
  generatedAt: string
}

/** Audit log entry */
export interface AuditLogEntry {
  id: string
  actorEmail: string
  action: string
  entityType: string
  entityId: string
  detail: string | null
  createdAt: string
}

/** Audit log page with optional meta note */
export interface AuditLogPage {
  items: AuditLogEntry[]
  pagination: {
    page: number
    size: number
    totalElements: number
    totalPages: number
    hasNext: boolean
  }
  meta?: {
    note?: string
  }
}
