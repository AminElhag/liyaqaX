import { useTranslation } from 'react-i18next'
import type { Member } from '@/types/domain'
import { MemberStatusBadge } from './MemberStatusBadge'

interface MemberProfileCardProps {
  member: Member
}

export function MemberProfileCard({ member }: MemberProfileCardProps) {
  const { t, i18n } = useTranslation()
  const isAr = i18n.language === 'ar'

  const fullName = isAr
    ? `${member.firstNameAr} ${member.lastNameAr}`
    : `${member.firstNameEn} ${member.lastNameEn}`

  const branchName = isAr ? member.branch.nameAr : member.branch.nameEn

  return (
    <div className="space-y-6">
      {/* Member card */}
      <div className="rounded-lg border border-gray-200 bg-white p-6">
        <div className="flex items-start gap-4">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-gray-200 text-xl font-semibold text-gray-500">
            {member.firstNameEn.charAt(0)}
            {member.lastNameEn.charAt(0)}
          </div>
          <div className="flex-1">
            <div className="flex items-center gap-3">
              <h2 className="text-lg font-semibold text-gray-900">
                {fullName}
              </h2>
              <MemberStatusBadge status={member.membershipStatus} />
            </div>
            <p className="mt-1 text-sm text-gray-500">{member.email}</p>
          </div>
        </div>

        <dl className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <Field label={t('members.form.phone')} value={member.phone} />
          <Field
            label={t('members.form.branch')}
            value={branchName}
          />
          <Field
            label={t('members.columns.joinedAt')}
            value={member.joinedAt}
          />
          {member.nationalId && (
            <Field
              label={t('members.form.nationalId')}
              value={member.nationalId}
            />
          )}
          {member.dateOfBirth && (
            <Field
              label={t('members.form.dateOfBirth')}
              value={member.dateOfBirth}
            />
          )}
          {member.gender && (
            <Field
              label={t('members.form.gender')}
              value={t(`members.form.genderOptions.${member.gender}`)}
            />
          )}
        </dl>

        {member.notes && (
          <div className="mt-4 rounded-md bg-gray-50 p-3">
            <p className="text-xs font-medium text-gray-500 uppercase">
              {t('members.form.notes')}
            </p>
            <p className="mt-1 text-sm text-gray-700">{member.notes}</p>
          </div>
        )}
      </div>

      {/* Waiver status */}
      <div className="rounded-lg border border-gray-200 bg-white p-6">
        <h3 className="text-sm font-semibold text-gray-900 uppercase tracking-wide">
          {member.hasSignedWaiver
            ? t('members.waiver.signed')
            : t('members.waiver.notSigned')}
        </h3>
        <div className="mt-2">
          <span
            className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${
              member.hasSignedWaiver
                ? 'bg-green-100 text-green-700'
                : 'bg-amber-100 text-amber-700'
            }`}
          >
            {member.hasSignedWaiver
              ? t('members.waiver.signed')
              : t('members.waiver.notSigned')}
          </span>
        </div>
      </div>

      {/* Emergency contacts */}
      <div className="rounded-lg border border-gray-200 bg-white p-6">
        <h3 className="text-sm font-semibold text-gray-900 uppercase tracking-wide">
          {t('members.profile.emergencyContacts')}
        </h3>
        <div className="mt-4 space-y-3">
          {member.emergencyContacts.map((contact) => {
            const contactName = isAr ? contact.nameAr : contact.nameEn
            return (
              <div
                key={contact.id}
                className="flex items-center justify-between rounded-md border border-gray-100 bg-gray-50 px-4 py-3"
              >
                <div>
                  <p className="text-sm font-medium text-gray-900">
                    {contactName}
                  </p>
                  {contact.relationship && (
                    <p className="text-xs text-gray-500">
                      {contact.relationship}
                    </p>
                  )}
                </div>
                <p className="text-sm text-gray-600">{contact.phone}</p>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs font-medium text-gray-500 uppercase">{label}</dt>
      <dd className="mt-1 text-sm text-gray-900">{value}</dd>
    </div>
  )
}
