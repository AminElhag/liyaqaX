export interface OtpVerifyResponse {
  accessToken: string
  member: {
    id: string
    firstName: string
    lastName: string
    preferredLanguage: string | null
  }
}

export interface MemberMe {
  id: string
  firstName: string
  lastName: string
  firstNameAr: string | null
  lastNameAr: string | null
  phone: string
  email: string | null
  preferredLanguage: string | null
  club: { id: string; name: string; nameAr: string }
  membership: MembershipSummary | null
}

export interface MembershipSummary {
  planName: string
  planNameAr: string
  status: string
  startDate: string
  expiresAt: string
  daysRemaining: number
}

export interface PortalSettings {
  gxBookingEnabled: boolean
  ptViewEnabled: boolean
  invoiceViewEnabled: boolean
  onlinePaymentEnabled: boolean
  portalMessage: string | null
}

export interface GxScheduleItem {
  id: string
  classType: { name: string; nameAr: string; color: string | null }
  instructorName: string
  startTime: string
  endTime: string
  capacity: number
  bookedCount: number
  spotsRemaining: number
  isBooked: boolean
}

export interface PtSession {
  id: string
  scheduledAt: string
  status: string
  trainerName: string
  packageName: string
  sessionsUsed: number
  sessionsTotal: number
}

export interface InvoiceSummary {
  id: string
  invoiceNumber: string
  issuedAt: string
  subtotalHalalas: number
  vatAmountHalalas: number
  totalHalalas: number
  zatcaStatus: string
}

export interface InvoiceDetail extends InvoiceSummary {
  zatcaQrCode: string | null
}
