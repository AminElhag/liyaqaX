import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

const registerClient = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
    'X-Client-App': 'web-arena',
  },
})

export interface SelfRegistrationRequest {
  nameEn?: string
  nameAr?: string
  email?: string
  dateOfBirth?: string
  gender?: string
  emergencyContactName?: string
  emergencyContactPhone?: string
  desiredMembershipPlanId?: string
}

export interface RegistrationCompleteResponse {
  memberId: string
  status: string
}

export interface RegistrationCheckResponse {
  selfRegistrationEnabled: boolean
}

export async function checkRegistrationEnabled(clubId: string): Promise<boolean> {
  const { data } = await registerClient.get<RegistrationCheckResponse>(
    `/arena/register/check?clubId=${clubId}`,
  )
  return data.selfRegistrationEnabled
}

export async function requestRegistrationOtp(phone: string, clubId: string): Promise<void> {
  await registerClient.post('/arena/register/otp/request', { phone, clubId })
}

export async function verifyRegistrationOtp(
  phone: string,
  otp: string,
  clubId: string,
): Promise<string> {
  const { data } = await registerClient.post<{ registrationToken: string }>(
    '/arena/register/otp/verify',
    { phone, otp, clubId },
  )
  return data.registrationToken
}

export async function completeRegistration(
  token: string,
  request: SelfRegistrationRequest,
): Promise<RegistrationCompleteResponse> {
  const { data } = await registerClient.post<RegistrationCompleteResponse>(
    '/arena/register/complete',
    request,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  return data
}
