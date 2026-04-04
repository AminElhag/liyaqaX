import { useState, useMemo } from 'react'
import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { startOfWeek, endOfWeek, addWeeks } from 'date-fns'
import { getClassInstances, gxKeys } from '@/api/gx'
import { GXScheduleGrid } from '@/components/gx/GXScheduleGrid'
import { useBranchStore } from '@/stores/useBranchStore'

export const Route = createFileRoute('/gx/')({
  component: GXSchedulePage,
})

function GXSchedulePage() {
  const { t } = useTranslation()
  const activeBranchId = useBranchStore((s) => s.activeBranch?.id)
  const [weekOffset, setWeekOffset] = useState(0)

  const weekStart = useMemo(() => {
    const base = startOfWeek(new Date(), { weekStartsOn: 0 })
    return weekOffset === 0 ? base : addWeeks(base, weekOffset)
  }, [weekOffset])

  const weekEnd = useMemo(() => endOfWeek(weekStart, { weekStartsOn: 0 }), [weekStart])

  const params = useMemo(
    () => ({
      branchId: activeBranchId ?? '',
      from: weekStart.toISOString(),
      to: weekEnd.toISOString(),
      size: 50,
    }),
    [activeBranchId, weekStart, weekEnd],
  )

  const { data, isLoading } = useQuery({
    queryKey: gxKeys.instanceList(params),
    queryFn: () => getClassInstances(params),
    enabled: !!activeBranchId,
    staleTime: 30_000,
  })

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">{t('gx.schedule.title')}</h1>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setWeekOffset((w) => w - 1)}
            className="rounded border px-3 py-1 text-sm hover:bg-gray-50"
          >
            {t('gx.schedule.prevWeek')}
          </button>
          <button
            onClick={() => setWeekOffset(0)}
            className="rounded border px-3 py-1 text-sm hover:bg-gray-50"
          >
            {t('gx.schedule.today')}
          </button>
          <button
            onClick={() => setWeekOffset((w) => w + 1)}
            className="rounded border px-3 py-1 text-sm hover:bg-gray-50"
          >
            {t('gx.schedule.nextWeek')}
          </button>
        </div>
      </div>

      {!activeBranchId ? (
        <p className="text-gray-500">{t('gx.schedule.selectBranch')}</p>
      ) : isLoading ? (
        <p className="text-gray-500">{t('common.loading')}</p>
      ) : (
        <GXScheduleGrid
          instances={data?.items ?? []}
          weekStart={weekStart}
        />
      )}
    </div>
  )
}
