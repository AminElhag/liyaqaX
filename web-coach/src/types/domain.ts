export interface TrainerProfile {
  id: string
  firstName: string
  lastName: string
  firstNameAr: string | null
  lastNameAr: string | null
  email: string
  phone: string | null
  trainerTypes: string[]
  club: ClubSummary
  branches: BranchSummary[]
  certifications: CertificationSummary[]
}

export interface ClubSummary {
  id: string
  name: string
  nameAr: string
}

export interface BranchSummary {
  id: string
  name: string
}

export interface CertificationSummary {
  id: string
  name: string
  issuingOrganization: string | null
  issueDate: string | null
  expiryDate: string | null
}

export interface ScheduleItem {
  type: 'pt' | 'gx'
  id: string
  startTime: string
  endTime: string | null
  title: string
  memberOrClassName: string
  status: string
  bookedCount?: number
  capacity?: number
}

export interface PtSession {
  id: string
  scheduledAt: string
  status: string
  memberName: string
  packageName: string
  notes: string | null
}

export interface GxClass {
  id: string
  classType: { name: string; nameAr: string; color: string | null }
  startTime: string
  endTime: string
  capacity: number
  bookedCount: number
  attendedCount: number
}

export interface GxBooking {
  id: string
  memberName: string
  bookedAt: string
  attended: boolean | null
}
