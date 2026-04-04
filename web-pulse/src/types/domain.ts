/** Localized name pair (Arabic + English) */
export interface LocalizedName {
  nameAr: string
  nameEn: string
}

/** Role summary embedded in staff responses */
export interface RoleSummary {
  id: string
  nameAr: string
  nameEn: string
}

/** Branch summary embedded in staff responses */
export interface BranchSummary {
  id: string
  nameAr: string
  nameEn: string
}

/** Staff member list row (from GET /api/v1/staff) */
export interface StaffMemberSummary {
  id: string
  firstNameAr: string
  firstNameEn: string
  lastNameAr: string
  lastNameEn: string
  email: string
  role: RoleSummary
  isActive: boolean
}

/** Full staff member detail (from GET /api/v1/staff/:id) */
export interface StaffMember {
  id: string
  userId: string
  organizationId: string
  clubId: string
  firstNameAr: string
  firstNameEn: string
  lastNameAr: string
  lastNameEn: string
  email: string
  phone: string | null
  nationalId: string | null
  role: RoleSummary
  branches: BranchSummary[]
  employmentType: string
  joinedAt: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}
