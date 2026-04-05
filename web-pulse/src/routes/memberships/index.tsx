import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ExpiringMembershipsTable } from '@/components/membership/ExpiringMembershipsTable'

export const Route = createFileRoute('/memberships/')({
  component: MembershipsPage,
})

function MembershipsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  return (
    <div className="space-y-6">
      <h2 className="text-xl font-semibold text-gray-900">
        {t('membership.expiringTitle')}
      </h2>

      <ExpiringMembershipsTable
        onRenew={(memberId) => {
          navigate({
            to: '/members/$memberId/membership',
            params: { memberId },
          })
        }}
      />
    </div>
  )
}
