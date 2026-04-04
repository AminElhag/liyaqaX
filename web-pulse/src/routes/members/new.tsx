import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { createMember } from '@/api/members'
import { getBranches, branchKeys } from '@/api/branches'
import { useAuthStore } from '@/stores/useAuthStore'
import { PageShell } from '@/components/layout/PageShell'
import { MemberRegistrationForm } from '@/components/members/MemberRegistrationForm'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import type { CreateMemberRequest } from '@/types/domain'
import type { ApiError } from '@/types/api'

export const Route = createFileRoute('/members/new')({
  component: NewMemberPage,
})

function NewMemberPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)

  const [createdPassword, setCreatedPassword] = useState<string | null>(null)
  const [createdMemberId, setCreatedMemberId] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  const orgId = user?.organizationId ?? ''
  const clubId = user?.clubId ?? ''

  const { data: branchData, isLoading: branchesLoading } = useQuery({
    queryKey: branchKeys.list(orgId, clubId),
    queryFn: () => getBranches(orgId, clubId),
    enabled: !!orgId && !!clubId,
  })

  const mutation = useMutation({
    mutationFn: createMember,
    onSuccess: (member) => {
      setCreatedMemberId(member.id)
    },
  })

  const handleSubmit = (data: CreateMemberRequest, generatedPassword: string) => {
    setCreatedPassword(generatedPassword)
    mutation.mutate(data)
  }

  const handleCopy = async () => {
    if (createdPassword) {
      await navigator.clipboard.writeText(createdPassword)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  // Success state — show generated password
  if (createdMemberId && createdPassword) {
    return (
      <PageShell title={t('members.registerMember')}>
        <div className="mx-auto max-w-md space-y-6 pt-8">
          <div className="rounded-lg border border-green-200 bg-green-50 p-6 text-center">
            <p className="text-sm font-medium text-green-800">
              {t('members.form.success')}
            </p>
          </div>

          <div className="rounded-lg border border-gray-200 bg-white p-6">
            <p className="mb-2 text-sm font-medium text-gray-700">
              {t('members.form.passwordGenerated')}
            </p>
            <div className="flex items-center gap-2">
              <code className="flex-1 rounded-md bg-gray-100 px-3 py-2 text-sm font-mono">
                {createdPassword}
              </code>
              <button
                type="button"
                onClick={handleCopy}
                className="rounded-md border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                {copied ? '✓' : t('members.form.copyPassword')}
              </button>
            </div>
          </div>

          <button
            type="button"
            onClick={() =>
              navigate({
                to: '/members/$memberId/overview',
                params: { memberId: createdMemberId },
              })
            }
            className="w-full rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-blue-700"
          >
            {t('members.profile.overview')}
          </button>
        </div>
      </PageShell>
    )
  }

  return (
    <PageShell title={t('members.registerMember')}>
      <div className="mx-auto max-w-2xl">
        {branchesLoading && <LoadingSkeleton rows={6} />}

        {mutation.error && (
          <div className="mb-4 rounded-md bg-red-50 p-4 text-sm text-red-700">
            {(mutation.error as unknown as ApiError).detail ?? t('common.error')}
          </div>
        )}

        {branchData && (
          <MemberRegistrationForm
            branches={branchData.items}
            onSubmit={handleSubmit}
            isSubmitting={mutation.isPending}
          />
        )}
      </div>
    </PageShell>
  )
}
