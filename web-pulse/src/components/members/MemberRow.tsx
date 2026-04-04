import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import type { MemberSummary } from '@/types/domain'
import { MemberStatusBadge } from './MemberStatusBadge'

interface MemberRowProps {
  member: MemberSummary
}

export function MemberRow({ member }: MemberRowProps) {
  const { i18n } = useTranslation()
  const navigate = useNavigate()
  const isAr = i18n.language === 'ar'

  const fullName = isAr
    ? `${member.firstNameAr} ${member.lastNameAr}`
    : `${member.firstNameEn} ${member.lastNameEn}`

  const branchName = isAr ? member.branch.nameAr : member.branch.nameEn

  return (
    <tr
      onClick={() =>
        navigate({
          to: '/members/$memberId',
          params: { memberId: member.id },
        })
      }
      className="cursor-pointer border-b border-gray-100 hover:bg-gray-50"
    >
      <td className="px-4 py-3 text-sm font-medium text-gray-900">
        {fullName}
      </td>
      <td className="px-4 py-3 text-sm text-gray-600">{member.email}</td>
      <td className="px-4 py-3 text-sm text-gray-600">{member.phone}</td>
      <td className="px-4 py-3">
        <MemberStatusBadge status={member.membershipStatus} />
      </td>
      <td className="px-4 py-3 text-sm text-gray-600">{branchName}</td>
      <td className="px-4 py-3 text-sm text-gray-600">{member.joinedAt}</td>
    </tr>
  )
}
