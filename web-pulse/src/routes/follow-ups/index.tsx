import { createFileRoute, Link } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { getFollowUps, memberNoteKeys, type FollowUpItem } from '@/api/memberNotes'
import { PageShell } from '@/components/layout/PageShell'
import { LoadingSkeleton } from '@/components/shared/LoadingSkeleton'
import { cn } from '@/lib/cn'

export const Route = createFileRoute('/follow-ups/')({
  component: FollowUpsPage,
})

function getRowColor(daysUntilDue: number): string {
  if (daysUntilDue <= 0) return 'bg-red-50'
  if (daysUntilDue === 1) return 'bg-amber-50'
  return ''
}

function FollowUpsPage() {
  const { t } = useTranslation()

  const { data, isLoading } = useQuery({
    queryKey: memberNoteKeys.followUps(),
    queryFn: getFollowUps,
    staleTime: 60_000,
  })

  const todayCount = data?.followUps.filter((f) => f.daysUntilDue <= 0).length ?? 0

  return (
    <PageShell
      title={t('followups.page_title')}
      actions={
        todayCount > 0 ? (
          <span className="rounded-full bg-red-500 px-2 py-0.5 text-xs font-bold text-white">
            {todayCount} {t('followups.due_today')}
          </span>
        ) : null
      }
    >
      {isLoading && <LoadingSkeleton rows={5} />}

      {data && data.followUps.length === 0 && (
        <div className="py-12 text-center text-sm text-gray-500">
          {t('followups.empty')}
        </div>
      )}

      {data && data.followUps.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200 text-start text-gray-500">
                <th className="px-3 py-2 font-medium">{t('followups.due_date')}</th>
                <th className="px-3 py-2 font-medium">{t('followups.member_name')}</th>
                <th className="px-3 py-2 font-medium">{t('followups.content')}</th>
                <th className="px-3 py-2 font-medium">{t('followups.created_by')}</th>
                <th className="px-3 py-2 font-medium">{t('followups.days_until_due')}</th>
              </tr>
            </thead>
            <tbody>
              {data.followUps.map((item: FollowUpItem) => (
                <tr
                  key={item.noteId}
                  className={cn('border-b border-gray-100', getRowColor(item.daysUntilDue))}
                >
                  <td className="px-3 py-2">
                    {new Intl.DateTimeFormat('en', {
                      month: 'short',
                      day: 'numeric',
                      year: 'numeric',
                      timeZone: 'Asia/Riyadh',
                    }).format(new Date(item.followUpAt))}
                  </td>
                  <td className="px-3 py-2">
                    <Link
                      to="/members/$memberId/notes"
                      params={{ memberId: item.memberPublicId }}
                      className="text-blue-600 hover:underline"
                    >
                      {item.memberName}
                    </Link>
                  </td>
                  <td className="max-w-xs truncate px-3 py-2 text-gray-600">
                    {item.content.length > 80
                      ? `${item.content.substring(0, 80)}...`
                      : item.content}
                  </td>
                  <td className="px-3 py-2 text-gray-500">{item.createdByName}</td>
                  <td className="px-3 py-2">
                    {item.daysUntilDue <= 0 ? (
                      <span className="font-medium text-red-600">
                        {t('followups.due_today')}
                      </span>
                    ) : item.daysUntilDue === 1 ? (
                      <span className="font-medium text-amber-600">
                        {t('followups.due_tomorrow')}
                      </span>
                    ) : (
                      <span className="text-gray-600">
                        {item.daysUntilDue} {t('followups.days_until_due')}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </PageShell>
  )
}
