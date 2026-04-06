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

/** Role list item returned by GET /api/v1/roles */
export interface RoleListItem {
  id: string
  nameAr: string
  nameEn: string
  description: string | null
  scope: string
  isSystem: boolean
  permissionCount: number
  staffCount: number
}

/** Role detail returned by GET /api/v1/roles/:id */
export interface RoleDetail {
  id: string
  nameAr: string
  nameEn: string
  description: string | null
  scope: string
  isSystem: boolean
  permissions: PermissionItem[]
  staffCount: number
}

/** Permission item */
export interface PermissionItem {
  id: string
  code: string
  description: string | null
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
  freezeAllowed: boolean
  maxFreezeDays: number
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

/** Request body for POST /api/v1/members/:id/memberships/:id/renew */
export interface RenewMembershipRequest {
  planId: string
  startDate?: string
  paymentMethod: 'cash' | 'card' | 'bank-transfer' | 'other'
  amountHalalas: number
  referenceNumber?: string
  notes?: string
}

/** Request body for POST /api/v1/members/:id/memberships/:id/freeze */
export interface FreezeMembershipRequest {
  freezeStartDate: string
  freezeEndDate: string
  reason?: string
}

/** Request body for POST /api/v1/members/:id/memberships/:id/unfreeze */
export interface UnfreezeMembershipRequest {
  notes?: string
}

/** Request body for POST /api/v1/members/:id/memberships/:id/terminate */
export interface TerminateMembershipRequest {
  reason: string
}

/** Expiring membership row (from GET /api/v1/memberships/expiring) */
export interface ExpiringMembership {
  memberId: string
  memberName: string
  memberPhone: string
  planNameAr: string
  planNameEn: string
  endDate: string
  daysRemaining: number
  membershipId: string
  membershipStatus: string
}

/** Freeze period record */
export interface FreezePeriod {
  id: string
  freezeStartDate: string
  freezeEndDate: string
  actualEndDate: string | null
  durationDays: number
  reason: string | null
  createdAt: string
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

// ── GX (Group Exercise) domain types ────────────────────────────────────────

export type GXInstanceStatus =
  | 'scheduled'
  | 'in-progress'
  | 'completed'
  | 'cancelled'

export type GXBookingStatus =
  | 'confirmed'
  | 'cancelled'
  | 'waitlist'
  | 'promoted'

export type GXAttendanceStatus = 'present' | 'absent' | 'late'

/** GX class type (template) response */
export interface GXClassType {
  id: string
  nameAr: string
  nameEn: string
  descriptionAr: string | null
  descriptionEn: string | null
  defaultDurationMinutes: number
  defaultCapacity: number
  color: string | null
  isActive: boolean
  createdAt: string
  updatedAt: string
}

/** Class type summary embedded in instance responses */
export interface GXClassTypeSummary {
  id: string
  nameAr: string
  nameEn: string
  color: string | null
}

/** Instructor summary embedded in instance responses */
export interface GXInstructorSummary {
  id: string
  firstNameAr: string
  firstNameEn: string
  lastNameAr: string
  lastNameEn: string
}

/** GX class instance (scheduled occurrence) response */
export interface GXClassInstance {
  id: string
  classType: GXClassTypeSummary
  instructor: GXInstructorSummary
  scheduledAt: string
  durationMinutes: number
  capacity: number
  bookingsCount: number
  waitlistCount: number
  availableSpots: number
  room: string | null
  status: GXInstanceStatus
  notes: string | null
  createdAt: string
}

/** Member summary embedded in booking responses */
export interface GXMemberSummary {
  id: string
  firstNameAr: string
  firstNameEn: string
  lastNameAr: string
  lastNameEn: string
}

/** GX booking response */
export interface GXBooking {
  id: string
  instanceId: string
  member: GXMemberSummary
  status: GXBookingStatus
  waitlistPosition: number | null
  bookedAt: string
  cancelledAt: string | null
}

/** GX attendance response */
export interface GXAttendance {
  id: string
  instanceId: string
  member: GXMemberSummary
  status: GXAttendanceStatus
  markedAt: string
}

/** Request body for POST /api/v1/gx/class-types */
export interface CreateGXClassTypeRequest {
  nameAr: string
  nameEn: string
  descriptionAr?: string
  descriptionEn?: string
  defaultDurationMinutes?: number
  defaultCapacity?: number
  color?: string
}

/** Request body for POST /api/v1/gx/classes */
export interface CreateGXClassInstanceRequest {
  classTypeId: string
  instructorId: string
  scheduledAt: string
  durationMinutes?: number
  capacity?: number
  room?: string
  notes?: string
}

/** Request body for POST /api/v1/gx/classes/:id/bookings */
export interface BookMemberRequest {
  memberId: string
}

/** Single entry in bulk attendance request */
export interface AttendanceEntry {
  memberId: string
  status: GXAttendanceStatus
}

/** Request body for POST /api/v1/gx/classes/:id/attendance */
export interface BulkAttendanceRequest {
  attendance: AttendanceEntry[]
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
  zatcaUuid: string | null
  zatcaHash: string | null
  zatcaQrCode: string | null
  previousInvoiceHash: string | null
  invoiceCounterValue: number | null
}

export type ZatcaStatus = 'pending' | 'generated' | 'submitted' | 'accepted' | 'rejected'

// ── Lead Pipeline ───────────────────────────────────────────────────────────

export type LeadStage = 'new' | 'contacted' | 'interested' | 'converted' | 'lost'

export interface LeadSourceSummary {
  id: string
  name: string
  nameAr: string
  color: string
}

export interface LeadStaffSummary {
  id: string
  firstName: string
  lastName: string
}

export interface LeadBranchSummary {
  id: string
  nameAr: string
  nameEn: string
}

export interface LeadSummary {
  id: string
  firstName: string
  lastName: string
  phone: string | null
  stage: LeadStage
  leadSource: LeadSourceSummary | null
  assignedStaff: LeadStaffSummary | null
  createdAt: string
}

export interface Lead {
  id: string
  firstName: string
  lastName: string
  firstNameAr: string | null
  lastNameAr: string | null
  phone: string | null
  email: string | null
  gender: string | null
  stage: LeadStage
  lostReason: string | null
  leadSource: LeadSourceSummary | null
  assignedStaff: LeadStaffSummary | null
  branch: LeadBranchSummary | null
  convertedMemberId: string | null
  contactedAt: string | null
  interestedAt: string | null
  convertedAt: string | null
  lostAt: string | null
  notes: string | null
  createdAt: string
  updatedAt: string
}

export interface LeadNote {
  id: string
  body: string
  staff: LeadStaffSummary
  createdAt: string
}

export interface LeadSource {
  id: string
  name: string
  nameAr: string
  color: string
  isActive: boolean
  displayOrder: number
  leadCount: number
  createdAt: string
  updatedAt: string
}

export interface CreateLeadRequest {
  firstName: string
  lastName: string
  firstNameAr?: string
  lastNameAr?: string
  phone?: string
  email?: string
  gender?: string
  leadSourceId?: string
  assignedStaffId?: string
  branchId?: string
  notes?: string
}

export interface UpdateLeadRequest {
  firstName?: string
  lastName?: string
  firstNameAr?: string
  lastNameAr?: string
  phone?: string
  email?: string
  gender?: string
  leadSourceId?: string
  assignedStaffId?: string
  branchId?: string
  notes?: string
}

export interface StageTransitionRequest {
  stage: 'new' | 'contacted' | 'interested' | 'lost'
  lostReason?: string
}

export interface ConvertLeadRequest {
  branchId: string
  membershipPlanId?: string
}

export interface CreateLeadNoteRequest {
  body: string
}

export interface CreateLeadSourceRequest {
  name: string
  nameAr: string
  color?: string
  displayOrder?: number
}

export interface UpdateLeadSourceRequest {
  name?: string
  nameAr?: string
  color?: string
  displayOrder?: number
}

// ── Cash Drawer ────────────────────────────────────────────────────────────

export interface MoneyValue {
  halalas: number
  sar: string
}

export interface CashDrawerStaffSummary {
  id: string
  firstName: string
  lastName: string
}

export interface CashDrawerBranchSummary {
  id: string
  name: string
}

export type CashDrawerSessionStatus = 'open' | 'closed' | 'reconciled'
export type CashDrawerEntryType = 'cash_in' | 'cash_out' | 'float_adjustment'
export type ReconciliationStatus = 'approved' | 'flagged'

export interface CashDrawerSession {
  id: string
  status: CashDrawerSessionStatus
  branch: CashDrawerBranchSummary
  openedBy: CashDrawerStaffSummary
  closedBy: CashDrawerStaffSummary | null
  reconciledBy: CashDrawerStaffSummary | null
  openingFloat: MoneyValue
  countedClosing: MoneyValue | null
  expectedClosing: MoneyValue | null
  difference: MoneyValue | null
  reconciliationStatus: ReconciliationStatus | null
  reconciliationNotes: string | null
  openedAt: string
  closedAt: string | null
  reconciledAt: string | null
  totalCashIn: MoneyValue
  totalCashOut: MoneyValue
  entryCount: number
  createdAt: string
  updatedAt: string
}

export interface CashDrawerSessionSummary {
  id: string
  status: CashDrawerSessionStatus
  branch: CashDrawerBranchSummary
  openedBy: CashDrawerStaffSummary
  openingFloat: MoneyValue
  difference: MoneyValue | null
  reconciliationStatus: ReconciliationStatus | null
  openedAt: string
  closedAt: string | null
}

export interface CashDrawerEntry {
  id: string
  entryType: CashDrawerEntryType
  amount: MoneyValue
  description: string
  paymentId: string | null
  recordedBy: CashDrawerStaffSummary
  recordedAt: string
}

export interface OpenSessionRequest {
  openingFloatHalalas: number
}

export interface CloseSessionRequest {
  countedClosingHalalas: number
}

export interface ReconcileSessionRequest {
  reconciliationStatus: ReconciliationStatus
  reconciliationNotes?: string
}

export interface CreateEntryRequest {
  entryType: CashDrawerEntryType
  amountHalalas: number
  description: string
  paymentId?: string
}

// ── Report types ──────────────────────────────────────────────────────────

export interface ReportMoneyAmount {
  halalas: number
  sar: string
}

/** Revenue report response */
export interface RevenueReportResponse {
  summary: {
    totalRevenue: ReportMoneyAmount
    membershipRevenue: ReportMoneyAmount
    ptRevenue: ReportMoneyAmount
    otherRevenue: ReportMoneyAmount
    totalPayments: number
    averagePaymentValue: ReportMoneyAmount
    comparisonPeriodRevenue: ReportMoneyAmount
    growthPercent: number | null
  }
  periods: TimePeriodRevenue[]
}

export interface TimePeriodRevenue {
  label: string
  periodStart: string
  periodEnd: string
  totalRevenue: ReportMoneyAmount
  membershipRevenue: ReportMoneyAmount
  ptRevenue: ReportMoneyAmount
  otherRevenue: ReportMoneyAmount
  paymentCount: number
}

/** Retention report response */
export interface RetentionReportResponse {
  summary: {
    activeMembers: number
    expiredThisPeriod: number
    newMembersThisPeriod: number
    renewedThisPeriod: number
    churnRate: number
    retentionRate: number
    expiringNext30Days: number
  }
  periods: TimePeriodRetention[]
  atRisk: AtRiskMember[]
}

export interface TimePeriodRetention {
  label: string
  periodStart: string
  periodEnd: string
  newMembers: number
  renewals: number
  expired: number
  activeAtEnd: number
  churnRate: number
}

export interface AtRiskMember {
  memberId: string
  memberName: string
  membershipPlan: string
  expiresAt: string
  daysUntilExpiry: number
  lastPaymentDate: string | null
}

/** Lead funnel report response */
export interface LeadReportResponse {
  summary: {
    totalLeads: number
    byStage: Record<string, number>
    conversionRate: number
    avgDaysToConvert: number | null
    topSources: LeadSourceStat[]
  }
  periods: TimePeriodLeads[]
  lostReasons: LostReasonCount[]
}

export interface LeadSourceStat {
  sourceName: string
  sourceNameAr: string
  color: string
  count: number
  conversionRate: number
}

export interface LostReasonCount {
  reason: string
  count: number
}

export interface TimePeriodLeads {
  label: string
  periodStart: string
  periodEnd: string
  newLeads: number
  converted: number
  lost: number
  conversionRate: number
}

/** Cash drawer report response */
export interface CashDrawerReportResponse {
  summary: {
    totalSessions: number
    totalCashIn: ReportMoneyAmount
    totalCashOut: ReportMoneyAmount
    netCash: ReportMoneyAmount
    totalShortages: ReportMoneyAmount
    totalSurpluses: ReportMoneyAmount
    sessionsWithDiscrepancy: number
    reconciliationRate: number
  }
  periods: TimePeriodCashDrawer[]
}

export interface TimePeriodCashDrawer {
  label: string
  periodStart: string
  periodEnd: string
  sessionCount: number
  totalCashIn: ReportMoneyAmount
  totalCashOut: ReportMoneyAmount
  netCash: ReportMoneyAmount
  shortages: ReportMoneyAmount
  surpluses: ReportMoneyAmount
}
