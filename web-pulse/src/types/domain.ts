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

// ── Member domain types ──────────────────────────────────────────────────────

export type MembershipStatus =
  | 'pending'
  | 'active'
  | 'frozen'
  | 'expired'
  | 'terminated'

export type Gender = 'male' | 'female' | 'unspecified'

/** Emergency contact embedded in member responses */
export interface EmergencyContact {
  id: string
  nameAr: string
  nameEn: string
  phone: string
  relationship: string | null
  createdAt: string
}

/** Member list row (from GET /api/v1/members) */
export interface MemberSummary {
  id: string
  firstNameAr: string
  firstNameEn: string
  lastNameAr: string
  lastNameEn: string
  email: string
  phone: string
  membershipStatus: MembershipStatus
  branch: BranchSummary
  joinedAt: string
}

/** Full member detail (from GET /api/v1/members/:id) */
export interface Member {
  id: string
  userId: string
  organizationId: string
  clubId: string
  branch: BranchSummary
  firstNameAr: string
  firstNameEn: string
  lastNameAr: string
  lastNameEn: string
  email: string
  phone: string
  nationalId: string | null
  dateOfBirth: string | null
  gender: Gender | null
  membershipStatus: MembershipStatus
  notes: string | null
  joinedAt: string
  emergencyContacts: EmergencyContact[]
  hasSignedWaiver: boolean
  createdAt: string
  updatedAt: string
}

/** Waiver signing status (from GET /api/v1/members/:id/waiver-status) */
export interface WaiverStatus {
  hasSignedCurrentWaiver: boolean
  waiverId: string | null
  waiverVersion: number | null
  signedAt: string | null
}

/** Request body for POST /api/v1/members */
export interface CreateMemberRequest {
  email: string
  password: string
  firstNameAr: string
  firstNameEn: string
  lastNameAr: string
  lastNameEn: string
  phone: string
  nationalId?: string
  dateOfBirth?: string
  gender?: Gender
  branchId: string
  notes?: string
  emergencyContact: {
    nameAr: string
    nameEn: string
    phone: string
    relationship?: string
  }
}

/** Request body for PATCH /api/v1/members/:id */
export interface UpdateMemberRequest {
  firstNameAr?: string
  firstNameEn?: string
  lastNameAr?: string
  lastNameEn?: string
  phone?: string
  nationalId?: string
  dateOfBirth?: string
  gender?: Gender
  notes?: string
}

/** Request body for POST /api/v1/members/:id/emergency-contacts */
export interface CreateEmergencyContactRequest {
  nameAr: string
  nameEn: string
  phone: string
  relationship?: string
}

// ── Membership Plan domain types ────────────────────────────────────────────

/** Full membership plan detail (from GET /api/v1/membership-plans/:id) */
export interface MembershipPlan {
  id: string
  organizationId: string
  clubId: string
  nameAr: string
  nameEn: string
  descriptionAr: string | null
  descriptionEn: string | null
  priceHalalas: number
  priceSar: string
  durationDays: number
  gracePeriodDays: number
  freezeAllowed: boolean
  maxFreezeDays: number
  gxClassesIncluded: boolean
  ptSessionsIncluded: boolean
  isActive: boolean
  sortOrder: number
  createdAt: string
  updatedAt: string
}

/** Plan list row (from GET /api/v1/membership-plans) */
export interface MembershipPlanSummary {
  id: string
  nameAr: string
  nameEn: string
  priceHalalas: number
  priceSar: string
  durationDays: number
  isActive: boolean
}

/** Request body for POST /api/v1/membership-plans */
export interface CreateMembershipPlanRequest {
  nameAr: string
  nameEn: string
  descriptionAr?: string
  descriptionEn?: string
  priceHalalas: number
  durationDays: number
  gracePeriodDays?: number
  freezeAllowed?: boolean
  maxFreezeDays?: number
  gxClassesIncluded?: boolean
  ptSessionsIncluded?: boolean
  sortOrder?: number
}

/** Request body for PATCH /api/v1/membership-plans/:id */
export interface UpdateMembershipPlanRequest {
  nameAr?: string
  nameEn?: string
  descriptionAr?: string
  descriptionEn?: string
  priceHalalas?: number
  durationDays?: number
  gracePeriodDays?: number
  freezeAllowed?: boolean
  maxFreezeDays?: number
  gxClassesIncluded?: boolean
  ptSessionsIncluded?: boolean
  isActive?: boolean
  sortOrder?: number
}

// ── Membership (instance) domain types ─────────────────────────────────────

/** Plan summary embedded in membership responses */
export interface MembershipPlanSummaryInfo {
  id: string
  nameAr: string
  nameEn: string
  priceHalalas: number
  priceSar: string
  durationDays: number
}

/** Payment info embedded in membership responses */
export interface MembershipPaymentInfo {
  id: string
  amountHalalas: number
  amountSar: string
  paymentMethod: string
  paidAt: string
}

/** Invoice info embedded in membership responses */
export interface MembershipInvoiceInfo {
  id: string
  invoiceNumber: string
  totalHalalas: number
  totalSar: string
  issuedAt: string
}

/** Full membership response (from GET /api/v1/members/:id/memberships/active) */
export interface Membership {
  id: string
  memberId: string
  plan: MembershipPlanSummaryInfo
  status: MembershipStatus
  startDate: string
  endDate: string
  graceEndDate: string | null
  freezeDaysUsed: number
  payment: MembershipPaymentInfo | null
  invoice: MembershipInvoiceInfo | null
  createdAt: string
}

/** Membership history row */
export interface MembershipSummary {
  id: string
  planNameAr: string
  planNameEn: string
  status: string
  startDate: string
  endDate: string
  amountHalalas: number
  amountSar: string
  paymentMethod: string | null
}

/** Request body for POST /api/v1/members/:id/memberships */
export interface AssignMembershipRequest {
  planId: string
  startDate?: string
  paymentMethod: 'cash' | 'card' | 'bank-transfer' | 'other'
  amountHalalas: number
  referenceNumber?: string
  notes?: string
}

// ── Payment domain types ───────────────────────────────────────────────────

export type PaymentMethod = 'cash' | 'card' | 'bank-transfer' | 'other'

/** Payment response (from GET /api/v1/members/:id/payments) */
export interface Payment {
  id: string
  memberId: string
  memberName: string
  amountHalalas: number
  amountSar: string
  paymentMethod: string
  referenceNumber: string | null
  invoiceNumber: string | null
  collectedBy: string
  paidAt: string
}

// ── Invoice domain types ───────────────────────────────────────────────────

/** Invoice response (from GET /api/v1/members/:id/invoices) */
export interface Invoice {
  id: string
  invoiceNumber: string
  memberId: string
  memberName: string
  subtotalHalalas: number
  subtotalSar: string
  vatRate: number
  vatAmountHalalas: number
  vatAmountSar: string
  totalHalalas: number
  totalSar: string
  paymentMethod: string
  issuedAt: string
  zatcaStatus: string
}
