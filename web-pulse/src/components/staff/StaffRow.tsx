import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import type { StaffMemberSummary } from '@/types/domain'
import { StaffStatusBadge } from './StaffStatusBadge'

interface StaffRowProps {
  staff: StaffMemberSummary
}

export function StaffRow({ staff }: StaffRowProps) {
  const { i18n } = useTranslation()
  const navigate = useNavigate()
  const isAr = i18n.language === 'ar'

  const fullName = isAr
    ? `${staff.firstNameAr} ${staff.lastNameAr}`
    : `${staff.firstNameEn} ${staff.lastNameEn}`

  const roleName = isAr ? staff.role.nameAr : staff.role.nameEn

  return (
    <tr
      onClick={() => navigate({ to: '/staff/$staffId', params: { staffId: staff.id } })}
      className="cursor-pointer border-b border-gray-100 hover:bg-gray-50"
    >
      <td className="px-4 py-3 text-sm font-medium text-gray-900">
        {fullName}
      </td>
      <td className="px-4 py-3 text-sm text-gray-600">{staff.email}</td>
      <td className="px-4 py-3 text-sm text-gray-600">{roleName}</td>
      <td className="px-4 py-3">
        <StaffStatusBadge isActive={staff.isActive} />
      </td>
    </tr>
  )
}
