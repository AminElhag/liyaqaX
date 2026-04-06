import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getLead, getLeadNotes, addLeadNote, moveLeadStage, convertLead, leadKeys } from '@/api/leads'
import { PageShell } from '@/components/layout/PageShell'
import { LeadStageBadge } from '@/components/lead/LeadStageBadge'
import { LeadSourceBadge } from '@/components/lead/LeadSourceBadge'
import { LeadNoteForm } from '@/components/lead/LeadNoteForm'
import { ConvertLeadModal } from '@/components/lead/ConvertLeadModal'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { PermissionGate } from '@/components/shared/PermissionGate'
import { Permission } from '@/types/permissions'
import type { LeadStage } from '@/types/domain'

export const Route = createFileRoute('/leads/$leadId')({
  component: LeadDetailPage,
})

function LeadDetailPage() {
  const { leadId } = Route.useParams()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [isConvertOpen, setIsConvertOpen] = useState(false)

  const { data: lead, isLoading } = useQuery({
    queryKey: leadKeys.detail(leadId),
    queryFn: () => getLead(leadId),
  })

  const { data: notes } = useQuery({
    queryKey: leadKeys.notes(leadId),
    queryFn: () => getLeadNotes(leadId),
    enabled: !!lead,
  })

  const addNoteMutation = useMutation({
    mutationFn: (body: string) => addLeadNote(leadId, { body }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: leadKeys.notes(leadId) })
    },
  })

  const stageMutation = useMutation({
    mutationFn: (params: { stage: string; lostReason?: string }) =>
      moveLeadStage(leadId, {
        stage: params.stage as 'new' | 'contacted' | 'interested' | 'lost',
        lostReason: params.lostReason,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: leadKeys.detail(leadId) })
      queryClient.invalidateQueries({ queryKey: leadKeys.lists() })
    },
  })

  const convertMutation = useMutation({
    mutationFn: (branchId: string) => convertLead(leadId, { branchId }),
    onSuccess: (data) => {
      setIsConvertOpen(false)
      queryClient.invalidateQueries({ queryKey: leadKeys.all })
      if (data.convertedMemberId) {
        navigate({ to: '/members/$memberId', params: { memberId: data.convertedMemberId } })
      }
    },
  })

  if (isLoading) {
    return (
      <PageShell title={t('leads.detail')}>
        <LoadingSkeleton rows={6} />
      </PageShell>
    )
  }

  if (!lead) {
    return (
      <PageShell title={t('leads.detail')}>
        <div className="rounded-md bg-red-50 p-4 text-sm text-red-700">
          {t('leads.notFound')}
        </div>
      </PageShell>
    )
  }

  const isConverted = lead.stage === 'converted'
  const isLost = lead.stage === 'lost'

  const stageOrder: LeadStage[] = ['new', 'contacted', 'interested']

  return (
    <PageShell title={`${lead.firstName} ${lead.lastName}`}>
      {/* Stage progression bar */}
      <div className="mb-6 flex items-center gap-2">
        {stageOrder.map((s, i) => (
          <div key={s} className="flex items-center gap-2">
            {i > 0 && <span className="text-gray-300">&rarr;</span>}
            <span
              className={`rounded-full px-3 py-1 text-xs font-medium ${
                lead.stage === s
                  ? 'bg-blue-600 text-white'
                  : stageOrder.indexOf(lead.stage as LeadStage) > i || isConverted
                    ? 'bg-green-100 text-green-700'
                    : 'bg-gray-100 text-gray-500'
              }`}
            >
              {t(`leads.stage.${s}`)}
            </span>
          </div>
        ))}
        <span className="text-gray-300">&rarr;</span>
        <LeadStageBadge stage={isConverted ? 'converted' : isLost ? 'lost' : lead.stage} />
      </div>

      {isConverted && lead.convertedMemberId && (
        <div className="mb-4 rounded-md bg-green-50 p-3 text-sm text-green-800">
          {t('leads.convertedBanner')}{' '}
          <button
            type="button"
            onClick={() => navigate({ to: '/members/$memberId', params: { memberId: lead.convertedMemberId! } })}
            className="font-medium underline"
          >
            {t('leads.viewMember')}
          </button>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        {/* Left: Lead info */}
        <div className="space-y-4">
          <div className="rounded-lg border border-gray-200 p-4">
            <h3 className="mb-3 font-semibold">{t('leads.info')}</h3>
            <dl className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <dt className="text-gray-500">{t('leads.name')}</dt>
                <dd>{lead.firstName} {lead.lastName}</dd>
              </div>
              {lead.phone && (
                <div>
                  <dt className="text-gray-500">{t('leads.phone')}</dt>
                  <dd dir="ltr">{lead.phone}</dd>
                </div>
              )}
              {lead.email && (
                <div>
                  <dt className="text-gray-500">{t('leads.email')}</dt>
                  <dd>{lead.email}</dd>
                </div>
              )}
              <div>
                <dt className="text-gray-500">{t('leads.source')}</dt>
                <dd><LeadSourceBadge source={lead.leadSource} /></dd>
              </div>
              <div>
                <dt className="text-gray-500">{t('leads.assignedStaff')}</dt>
                <dd>
                  {lead.assignedStaff
                    ? `${lead.assignedStaff.firstName} ${lead.assignedStaff.lastName}`
                    : '-'}
                </dd>
              </div>
            </dl>
          </div>

          {/* Actions */}
          {!isConverted && (
            <div className="flex flex-wrap gap-2">
              <PermissionGate permission={Permission.LEAD_UPDATE}>
                {lead.stage === 'new' && (
                  <button
                    type="button"
                    onClick={() => stageMutation.mutate({ stage: 'contacted' })}
                    className="rounded-md bg-blue-600 px-3 py-1.5 text-sm text-white hover:bg-blue-700"
                  >
                    {t('leads.action.markContacted')}
                  </button>
                )}
                {lead.stage === 'contacted' && (
                  <button
                    type="button"
                    onClick={() => stageMutation.mutate({ stage: 'interested' })}
                    className="rounded-md bg-purple-600 px-3 py-1.5 text-sm text-white hover:bg-purple-700"
                  >
                    {t('leads.action.markInterested')}
                  </button>
                )}
                {!isLost && (
                  <button
                    type="button"
                    onClick={() => {
                      const reason = window.prompt(t('leads.lost.reason'))
                      if (reason) stageMutation.mutate({ stage: 'lost', lostReason: reason })
                    }}
                    className="rounded-md border border-red-300 px-3 py-1.5 text-sm text-red-700 hover:bg-red-50"
                  >
                    {t('leads.action.markLost')}
                  </button>
                )}
                {isLost && (
                  <button
                    type="button"
                    onClick={() => stageMutation.mutate({ stage: 'new' })}
                    className="rounded-md border border-blue-300 px-3 py-1.5 text-sm text-blue-700 hover:bg-blue-50"
                  >
                    {t('leads.action.reopen')}
                  </button>
                )}
              </PermissionGate>
              <PermissionGate permission={Permission.LEAD_CONVERT}>
                {lead.stage === 'interested' && (
                  <button
                    type="button"
                    onClick={() => setIsConvertOpen(true)}
                    className="rounded-md bg-green-600 px-3 py-1.5 text-sm text-white hover:bg-green-700"
                  >
                    {t('leads.convert.title')}
                  </button>
                )}
              </PermissionGate>
            </div>
          )}
        </div>

        {/* Right: Notes timeline */}
        <div className="rounded-lg border border-gray-200 p-4">
          <h3 className="mb-3 font-semibold">{t('leads.notes.title')}</h3>
          <div className="mb-4 max-h-80 space-y-3 overflow-y-auto">
            {notes?.map((note) => (
              <div key={note.id} className="rounded-md bg-gray-50 p-3">
                <p className="text-sm text-gray-800">{note.body}</p>
                <div className="mt-1 flex items-center justify-between text-xs text-gray-400">
                  <span>{note.staff.firstName} {note.staff.lastName}</span>
                  <span>{new Date(note.createdAt).toLocaleDateString()}</span>
                </div>
              </div>
            ))}
            {(!notes || notes.length === 0) && (
              <p className="text-sm text-gray-400">{t('leads.notes.empty')}</p>
            )}
          </div>
          <LeadNoteForm
            onSubmit={(body) => addNoteMutation.mutate(body)}
            isLoading={addNoteMutation.isPending}
          />
        </div>
      </div>

      <ConvertLeadModal
        lead={lead}
        isOpen={isConvertOpen}
        onClose={() => setIsConvertOpen(false)}
        onConvert={(branchId) => convertMutation.mutate(branchId)}
        isLoading={convertMutation.isPending}
      />
    </PageShell>
  )
}
